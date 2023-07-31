package main;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
		name = "pathsum",
		mixinStandardHelpOptions = true,
		usageHelpAutoWidth = true,
		versionProvider = VersionProvider.class,
		description = {
				"Calculates or checks a list of checksums on a directory recursively.",
				"Requires specified algorithm and path, with optional export path.",
				"Report bugs to the GitHub at: https://github.com/UFFR/PathChecksum"},
		exitCodeListHeading = "%nExit Codes:%n",
		exitCodeList = {
				"0:Normal execution",
				"1:General exception",
				"2:Invalid parameter format"},
		footerHeading = "%n@|bold Example:|@%n",
		footer = {"java -jar path-checksum -p ~/Documents -a sha1 -e ~/Checksums -v"})
public class Main implements Callable<Integer>
{
	public static final Version VERSION = new Version(2, 0, 1);// v2.0.1r
	// Magnitudes
	public static final short KB = 1024;
	public static final int MB = KB * KB;
	public static final long GB = MB * KB, TB = GB * KB, PB = TB * KB;

	public static final int BUFFER = MB * 8;// 8 MB is roughly ideal size for most applications
	
	@Option(names = {"-p", "--path"}, required = true, paramLabel = "path", description = {"The path to check files. Defaults to running path."}, defaultValue = "")
	private Path inputPath;
	@Option(names = {"-e", "--export"}, paramLabel = "path", description = {"Export path of the checksums file or log report. Defaults to the input path."})
	private Path outputPath;
	@Option(names = {"-a", "--algorithm"}, paramLabel = "name", description = {"The algorithm to use for the checksum. Defaults to SHA-256.", "Any JRE supported algorithm may be used"}, defaultValue = "SHA-256", completionCandidates = AlgorithmCandidates.class)
	private MessageDigest digest;
	@Option(names = {"-c", "--check"}, description = {"Check and verify a checksum file instead of generating one."})
	private boolean checkMode;
	@Option(names = {"-v", "--verbose"}, description = {"Verbose printing of the progress.", "Ideally should only be used when exporting the result, since the output may be lengthy to copy-paste."})
	private boolean verbose;
	@Option(names = {"--force-absolute"}, description = {"Force the use of absolute paths in the output file.", "Useful in case the runtime location is the same as the input path and the full path is preferred."})
	private boolean absolutePathNames;
	
	private final StringBuilder builder = new StringBuilder();
	private long startTime;
	public Main()
	{}
	
	// Create a list of checksums
	public int createSum() throws IOException
	{
		// Notify user
		System.out.println("Recogized path as: " + inputPath.normalize().toAbsolutePath() + " and algorithm as: " + digest.getAlgorithm().toUpperCase() + (outputPath != null ? " with the output path: " + outputPath : " and no output path") + ", and is in hash mode.");
		System.out.println("Starting checksum of path: " + inputPath.normalize().toAbsolutePath());
		
		// Try to initially create output file, fails here instead of at the very end
		if (outputPath != null)
		{
			// Can't write to directory, convert to a file form
			if (Files.isDirectory(outputPath))
				outputPath = outputPath.resolve(nameProvider());
			
			Files.createFile(outputPath);
		}
		
		// If given path is a file instead of a folder/directory
		if (Files.isRegularFile(inputPath))
		{
			if (verbose)
				System.out.println("Path is actually file...");
			new FileChecksum(inputPath, getFileChecksum(inputPath.normalize(), digest), digest.getAlgorithm()).addToBuilder(builder, inputPath, absolutePathNames);
		} else
		{
			// Declare lists
			final List<Path> paths = new ArrayList<>();
			final List<FileChecksum> checksums;// Initialize later in case of very large directories
			if (verbose)
				System.out.println("Looking for files and folders...");
			// Pre-check for files recursively
			getAllPaths(inputPath, paths, verbose);
			// List the files it found
			if (verbose)
			{
				System.out.println("Found files:");
				iterableToList(paths, System.out::println, false);
				System.out.println("Calculating checksums...");
			}
			
			/// Ready to calculate the checksums
			// Initialize with already known size
			checksums = new ArrayList<>(paths.size());
			// Calculate from paths and add to checksums
			calculateFromFiles(paths, checksums, digest, verbose);
			// Sort based on depth then name
			checksums.sort(Comparator.comparing(FileChecksum::getFile, Comparator.comparingInt(Path::getNameCount).reversed().thenComparing(Comparator.naturalOrder())));
			// Accumulate the results
			checksums.forEach(c -> c.addToBuilder(builder, inputPath, absolutePathNames));
		}
		
		/// Outside since behavior is shared between branches
		/// Completed, print out results, export if necessary, and display time to complete
		System.out.println("\nFinished:\n");
		final String completeTime = timeToComplete(startTime, System.currentTimeMillis());
		System.out.println(builder);
		System.out.println();
		System.out.println(completeTime);
		if (outputPath != null)
			System.out.println("Exported to: " + Files.write(outputPath.toAbsolutePath(), builder.toString().getBytes()));
		
		return 0;
	}
	
	// Check a list of checksums, generated either by this program or by the GNU utilities checksum programs
	public int checkSum() throws IOException
	{
		// Notify user
		System.out.println("Recognized path as: " + inputPath.normalize().toAbsolutePath() + " and algorithm as: " + digest.getAlgorithm().toUpperCase() + ", and is in check mode.");
		System.out.println("Beginning to check...");
		
		// Declare lists and map in preparation for checks
		// Ordered paths is the order encountered in the list, failed paths for failed checksums, missing paths for files/directories not found
		final List<Path> orderedPaths = new ArrayList<>(), failedPaths = new ArrayList<>(), missingPaths = new ArrayList<>();
		// Improperly formatted lines
		final List<String> badFormats = new ArrayList<>();
		// For quick lookup of checksums given path
		final Map<Path, String> sumMap = new HashMap<>();
		
		// Different output file format, give header with basic statistics
		builder.append(" --- Checksum integrity report using ").append(inputPath).append(" on ").append(LocalDateTime.now()).append(" ---\n\n");
		
		// Accumulate all lines in file and pass tracking lists for parsing
		parseSumFile(Files.lines(inputPath).collect(ArrayList::new, List::add, List::addAll), orderedPaths, sumMap, missingPaths, badFormats, builder, verbose);
		// Begin checks
		checkFromFiles(orderedPaths, sumMap, digest, builder, verbose, failedPaths);
		
		// Report and log any tracked errors
		if (failedPaths.isEmpty())
			builder.append("\nNo failed checksums detected.\n");
		else
		{
			builder.append("\nDetected ").append(failedPaths.size()).append(" failed files!\n");
			iterableToList(failedPaths, builder::append, true);
		}
		if (!missingPaths.isEmpty())
		{
			builder.append("\nDetected ").append(missingPaths.size()).append(" missing files!\n");
			iterableToList(missingPaths, builder::append, true);
		}
		if (!badFormats.isEmpty())
		{
			builder.append("\nDetected ").append(badFormats.size()).append(" improperly formatted lines!\n");
			iterableToList(badFormats, builder::append, true);
		}
		
		/// Completed
		System.out.println("\nFinished\n");
		final String completeTime = timeToComplete(startTime, System.currentTimeMillis());
		builder.append('\n').append(completeTime);
		System.out.println(builder);
		if (outputPath != null)
			System.out.println("Exported to: " + Files.write(Paths.get(outputPath.normalize().toAbsolutePath().toString(), "checksum_report.log"), builder.toString().getBytes()));
		
		return 0;
	}
	
	// Main branching point
	@Override
	public Integer call() throws Exception
	{
		startTime = System.currentTimeMillis();
		return checkMode ? checkSum() : createSum();
	}
	
	// Get name of output file
	private String nameProvider()
	{
		if (outputPath != null)
			return Files.isDirectory(outputPath) ? inputPath.normalize().getFileName().toString() + '.' + extensionProvider() : outputPath.getFileName().toString();
		else
			return inputPath.normalize().toAbsolutePath().getFileName().toString() + '.' + extensionProvider();
			
	}
	
	// Get name of file extension, given digest algorithm
	private String extensionProvider()
	{
		final StringBuilder builder = new StringBuilder(digest.getAlgorithm());
		final int dashIndex = builder.indexOf("-");// Remove any dash
		if (dashIndex > 0)
			builder.deleteCharAt(dashIndex);
		final int slashIndex = builder.indexOf("/");
		if (slashIndex > 0)
			builder.setCharAt(slashIndex, '_');// Replace any slash with underscore
		return builder.toString().toLowerCase();
	}
	
	//// Entry point ////
	public static void main(String[] args)
	{
		final CommandLine commandLine = new CommandLine(new Main());
		commandLine.registerConverter(MessageDigest.class, MessageDigest::getInstance);// Register automatic digest algorithm getter
		final int exitCode = commandLine.execute(args);
		System.out.println("\nExited with code: " + exitCode);
		System.exit(exitCode);
	}

	// Convenience method
	private static String timeToComplete(long startTime, long completiontime)
	{
		return "Operation took: " + timeFromMillis(completiontime - startTime);
	}
	
	/**
	 * Check the files' current checksum against stored checksum.
	 * @param files Sorted list of files.
	 * @param sumMap Map of stored files and checksums.
	 * @param digest Selected digest algorithm instance.
	 * @param builder The {@code StringBuilder} for persistent log.
	 * @param verbose If extra console printing should be enabled.
	 * @param failedPaths List of paths with failed checksum matches.
	 * @throws IOException If any I/O exception occurs.
	 */
	private static void checkFromFiles(List<Path> files, Map<Path, String> sumMap, MessageDigest digest, StringBuilder builder, boolean verbose, List<Path> failedPaths) throws IOException
	{
		for (int i = 0; i < files.size(); i++)
		{
			// Convenience variables
			final Path file = files.get(i).normalize();// Normalize to remove redundancies that may be confusing to read
			final int count = i + 1;
			// Notify user
			if (verbose)
			{
				System.out.println("Checking checksum of: [" + file + ']');
				System.out.println("File #" + count + '/' + files.size());
			}
			
			// Get current and stored hashes
			final String hexHash = bytesToHex(true, getFileChecksum(file, digest)), storedHash = sumMap.get(file);
			// Check if equivalent and if valid
			final boolean success = hexHash.equalsIgnoreCase(storedHash), valid = hexHash.length() == storedHash.length();
			// Notify of result, regardless of verbosity
			System.out.println(file.toString() + ' ' + (success ? "OK" : "FAILED"));
			// Notify user if possibly wrong checksum used, if verbose and applicable
			if (verbose && !valid)
				System.err.println("Possibly wrong algorithm used. Calculated hash is: " + hexHash.length() + " characters long while stored hash is: " + storedHash.length() + " characters long.");
			
			// Log persistently
			builder.append('[').append(file).append(']').append(success ? " passed." : " failed!").append('\n');
			if (!valid)
				builder.append("Possibly wrong algorithm used. Calculated hash is: ").append(hexHash.length()).append(" characters long while stored hash is: ").append(storedHash.length()).append(" characters long.");
			if (!success)
				failedPaths.add(file);
		}
	}
	
	/**
	 * Parse the summary into the provided lists and track errors.
	 * @param lines The input lines.
	 * @param orderedFiles The parsed paths in order of the list.
	 * @param sumMap Convert the summary into a map.
	 * @param missingPaths Any paths not found.
	 * @param badFormats Improperly formatted lines.
	 * @param builder The {@code StringBuilder} for the persistent log.
	 * @param verbose If extra console printing should be enabled.
	 */
	private static void parseSumFile(List<String> lines, List<Path> orderedFiles, Map<Path, String> sumMap, List<Path> missingPaths, List<String> badFormats, StringBuilder builder, boolean verbose)
	{
		// Notify user of stage
		System.out.println("Parsing summary file...");
		// Track line count while using for-each loop
		for (int lineNum = 0; lineNum < lines.size(); lineNum++)
		{
			// Convenience
			final String checksumLine = lines.get(lineNum);
			try
			{
				// Try to find the double-space break index delimiting the checksum from the path
				final int breakIndex = checksumLine.indexOf("  ");
				// Split the checksum and path from each other
				final String storedHash = checksumLine.substring(0, breakIndex).toLowerCase();
				final Path filePath = Paths.get(checksumLine.substring(breakIndex + 2)).normalize();
				
				// Check if the hash has valid chars, note and skip if fails
				if (!validHash(storedHash))
				{
					noteBadFormat(verbose, checksumLine, lineNum + 1, badFormats, builder);
					continue;
				}
				
				// Check if exists, add to list and map if so, note if not
				if (Files.exists(filePath))
				{
					orderedFiles.add(filePath);
					sumMap.put(filePath, storedHash);
				} else
				{
					if (verbose)
						System.err.println(filePath + " NOT FOUND");
//					builder.append(filePath).append(" is missing!").append('\n');
					missingPaths.add(filePath);
				}
			} catch (StringIndexOutOfBoundsException e)// In case delimiter couldn't be found
			{
				noteBadFormat(verbose, checksumLine, lineNum + 1, badFormats, builder);
			}
		}
	}
	
	/**
	 * Checks if a checksum is valid.
	 * @param hash The string to check.
	 * @return True, if the string contains only 0-9 or a-f characters, false otherwise.
	 */
	private static boolean validHash(String hash)
	{
		for (char c : hash.toLowerCase().toCharArray())
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
				return false;
		return true;
	}
	
	/**
	 * Notify user and add to persistent log of improperly formatted line.
	 * @param verbose If should notify user immediately.
	 * @param checksumLine The failed line.
	 * @param lineNum The line # with the error.
	 * @param badFormats The tracking list to add to.
	 * @param builder The persistent log.
	 */
	private static void noteBadFormat(boolean verbose, String checksumLine, int lineNum, List<String> badFormats, StringBuilder builder)
	{
		if (verbose)
			System.err.println("Improperly formatted line detected: " + checksumLine);
//		builder.append("Line: #").append(lineNum).append(" [").append(checksumLine).append("] is improperly formatted.\n");
		badFormats.add(checksumLine);
	}
	
	/**
	 * Calculate checksums from found files.
	 * @param files File list.
	 * @param checksums List of checksums to add to.
	 * @param digest Digest algorithm instance to use.
	 * @param verbose If extra console printing should be enabled.
	 * @throws IOException If any I/O exception occurs.
	 */
	private static void calculateFromFiles(List<Path> files, List<FileChecksum> checksums, MessageDigest digest, boolean verbose) throws IOException
	{
		final int total = files.size();
		for (int i = 0; i < files.size(); i++)
		{
			// Convenience variables
			final Path file = files.get(i).normalize();// Normalize to remove redundancies
			final int count = i + 1;
			if (verbose)
			{
				System.out.println("Calculating checksum of: [" + file + ']');
				System.out.println("File #" + count + '/' + total);
			}
			final byte[] hashBytes = getFileChecksum(file, digest);
			final FileChecksum checksum = new FileChecksum(file, hashBytes, digest.getAlgorithm());
			if (verbose)
				System.out.println("Checksum calculated as: " + checksum.getHash());
			checksums.add(checksum);
		}
	}
	
	/**
	 * Recursively adds all paths within the path if a folder/directory.
	 * @param start The starting point of at this level.
	 * @param paths The list to collect to.
	 * @param verbose If extra console printing should be enabled.
	 * @throws IOException If any I/O exception occurs.
	 */
	private static void getAllPaths(Path start, List<Path> paths, boolean verbose) throws IOException
	{
		try (final DirectoryStream<Path> stream = Files.newDirectoryStream(start))
		{
			for (Path path : stream)
			{
				if (Files.isDirectory(path))
				{
					if (verbose)
						System.out.println("Found directory at: " + path.normalize());
					getAllPaths(path, paths, verbose);
				} else if (Files.isRegularFile(path))
				{
					final Path normalizedPath = path.normalize();
					if (verbose)
						System.out.println("Found file at: " + normalizedPath);
					paths.add(normalizedPath);
				} else if (verbose)
					System.err.println("Warning! Path [" + path.normalize() + "] no longer exists! Skipping path...");
			}
		}
	}
	
	/**
	 * Calculate the file's checksum in bytes.
	 * @param path The file the read.
	 * @param digest The selected digest algorithm instance to use.
	 * @return The checksum in raw bytes.
	 * @throws IOException If any I/O exception occurs.
	 */
	private static byte[] getFileChecksum(Path path, MessageDigest digest) throws IOException
	{
		// Get total size of file
		final long size = Files.size(path);
		final ProgressBarBuilder builder = new ProgressBarBuilder()
//				.setUnit("MB", MB)
				.showSpeed()
				.setInitialMax(size)
				.setTaskName("Reading...");
		if (size < KB * 2)
			builder.setUnit("B", 1);// If less than 2 KB
		else if (size < MB * 2)
			builder.setUnit("KB", KB);// If less than 2 MB
		else if (size < GB * 2)
			builder.setUnit("MB", MB);// If less than 2 GB
		else
			builder.setUnit("GB", GB);// If any larger
		
		// Read through digest stream and progress bar until done
		try (final InputStream inputStream = new DigestInputStream(ProgressBar.wrap(Files.newInputStream(path), builder), digest))
		{
			long read = 0;
			while (read < size)
			{
				final int alloc = (int) Math.min(BUFFER, size - read);
				final byte[] buffer = new byte[alloc];
				inputStream.read(buffer);
				read += alloc;
			}
			// Completed
			return digest.digest();
		}
	}
	
	/// Imported utilities ///
	
	static String bytesToHex(boolean pad, byte...bytes)
	{
		final StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
		{
			final String hex = Integer.toHexString(b & 0xff);
			appendAndPad(pad, 2, hex, builder);
		}
		return builder.toString();
	}
	
	private static void appendAndPad(boolean pad, int len, String end, StringBuilder builder)
	{
		if (pad && end.length() < len)
		{
			final char[] padding = new char[len - end.length()];
			Arrays.fill(padding, '0');
			builder.append(padding);
		}
		builder.append(end);
	}
	
	private static void iterableToList(Iterable<?> iterable, Consumer<String> appender, boolean newLine)
	{
		for (Object o : iterable)
		{
			final StringBuilder builder = new StringBuilder();
			builder.append("- ").append(o);
			if (newLine)
				builder.append('\n');
			appender.accept(builder.toString());
		}
	}
	
	public static String timeFromMillis(long timeIn)
	{
		final StringBuilder builder = new StringBuilder();
		final long secondMillis = 1000, minuteMillis = 60 * secondMillis, hourMillis = 60 * minuteMillis;
		long time = timeIn;
		if (time >= hourMillis)
		{
			builder.append(Math.floorDiv(time, hourMillis)).append(" hour(s) ");
			time %= hourMillis;
		}
		if (time >= minuteMillis)
		{
			builder.append(Math.floorDiv(time, minuteMillis)).append(" minute(s) ");
			time %= minuteMillis;
		}
		builder.append((double) time / secondMillis).append(" second(s)");
		return builder.append('.').toString();
	}
}
