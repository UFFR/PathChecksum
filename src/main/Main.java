package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.cli.AlreadySelectedException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.UnrecognizedOptionException;

public class Main
{
	private static final Options OPTIONS = new Options();
	private static final CommandLineParser PARSER = new DefaultParser();
	private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
	private static final StringBuilder BUILDER = new StringBuilder();
	private static final ArrayList<FileChecksum> FILE_CHECKSUMS = new ArrayList<>();
	static
	{
		final OptionGroup group = new OptionGroup();
		group.addOption(Option.builder("h").longOpt("help").desc("Print the help screen.").build());
		group.addOption(Option.builder("p").longOpt("path").desc("The path to check files. Defaults to running path.").hasArg().argName("check path").required().build());
		OPTIONS.addOption(Option.builder("c").longOpt("check").desc("Check and verify a checksum file instead of generating one.").build());
		OPTIONS.addOption(Option.builder("a").longOpt("algorithm").desc("The algorithm to use for the checksum. Defaults to SHA-256.").hasArg().argName("algorithm").optionalArg(true).build());
		OPTIONS.addOption(Option.builder("v").longOpt("verbose").desc("Verbose printing of the progress. Ideally should only be used when exporting the result, since the printing may be lengthy.").build());
		OPTIONS.addOption(Option.builder("e").longOpt("export").desc("Export path of the checksums file or log report. Defaults to the input path.").hasArg().argName("export path").optionalArg(true).build());
		OPTIONS.addOptionGroup(group);
	}
	private static Path inputPath = Paths.get("");
	private static Path outputPath;
	private static MessageDigest digest;
	private static final Supplier<String> EXTENSION_SUPPLIER = () ->
	{
		final StringBuilder builder = new StringBuilder(digest.getAlgorithm());
		final int dashIndex = builder.indexOf("-");
		if (dashIndex > 0)
			builder.deleteCharAt(dashIndex);
		final int slashIndex = builder.indexOf("/");
		if (slashIndex > 0)
			builder.deleteCharAt(slashIndex);
		return builder.toString().toLowerCase();
	};
	public static void main(String[] args)
	{
		try
		{
			final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			final CommandLine commandLine = PARSER.parse(OPTIONS, args);
			if (commandLine.hasOption('h'))
			{
				HELP_FORMATTER.printHelp("Requires specified algorithm and path, with optional export path.", "PathChecksum v1.5", OPTIONS, "Report bugs to the GitHub at: https://github.com/UFFR/PathChecksum", true);
				System.exit(0);
			}
			if (commandLine.getOptions().length == 0)
			{
				System.err.println("No arguments passed, program requires arguments!");
				System.exit(2);
			}
			if (commandLine.hasOption('p'))
				inputPath = Paths.get(commandLine.getOptionValue('p', ""));

			digest = MessageDigest.getInstance(commandLine.getOptionValue('a', "SHA-256").toUpperCase());
			if (commandLine.hasOption('e'))
				outputPath = Paths.get(commandLine.getOptionValue('e', inputPath.toString()));
			final boolean verbose = commandLine.hasOption('v');
			if (commandLine.hasOption('c'))
			{
				System.out.println("Recognized path as: " + inputPath + " and algorithm as: " + digest.getAlgorithm() + ", continue? (boolean)");
				if (Boolean.parseBoolean(reader.readLine()))
				{
					System.out.println("Starting check of checksum file...");
					final ArrayList<Path> failedPaths = new ArrayList<Path>();
					final ArrayList<Path> missingPaths = new ArrayList<Path>();
					final ArrayList<String> badFormats = new ArrayList<String>();
					BUILDER.append("Checksum integrity report using " + inputPath + " on " + new Date() + '\n');
					try (final Stream<String> stream = Files.lines(inputPath))
					{
						final Iterator<String> iterator = stream.iterator();
						while (iterator.hasNext())
						{
							final String checksumLine = iterator.next();
							try
							{
								final int breakIndex = checksumLine.indexOf("  ");
								final String storedHash = checksumLine.substring(0, breakIndex);
								final Path filePath = Paths.get(checksumLine.substring(breakIndex + 2));
								try
								{
									final String hexHash = bytesToHex(getFileChecksum(filePath));
									final boolean success = hexHash.equals(storedHash), valid = hexHash.length() == storedHash.length();
									if (verbose)
										System.out.println(filePath.toString() + ' ' + (success ? "OK" : "FAILED"));
									if (verbose && !valid)
										System.err.println("Possibly wrong algorithm used. Calculated hash is: " + hexHash.length() + " characters long while stored hash is: " + storedHash.length() + " characters long.");
									
									BUILDER.append(filePath).append(success ? " passed." : " failed!").append('\n');
									if (!valid)
										BUILDER.append("Possibly wrong algorithm used. Calculated hash is: ").append(hexHash.length()).append(" characters long while stored hash is: ").append(storedHash.length()).append(" characters long.");
									if (!success)
										failedPaths.add(filePath);
								} catch (NoSuchFileException e)
								{
									if (verbose)
										System.err.println(filePath.toString() + " NOT FOUND");
									BUILDER.append(filePath).append(" is missing!").append('\n');
									missingPaths.add(filePath);
								}
							} catch (StringIndexOutOfBoundsException e)
							{
								if (verbose)
									System.err.println("Improperly formatted line detected: " + checksumLine);
								BUILDER.append("Line: [").append(checksumLine).append("] is improperly formatted.");
								badFormats.add(checksumLine.toString());
							}
						}
					}
					if (failedPaths.isEmpty())
						BUILDER.append("\nNo failed checksums detected.\n");
					else
					{
						BUILDER.append("\nDetected " + failedPaths.size() + " failed files!\n");
						BUILDER.append(failedPaths);
					}
					if (!missingPaths.isEmpty())
					{
						BUILDER.append("\nDetected " + missingPaths.size() + " missing files!\n");
						BUILDER.append(missingPaths);
					}
					if (!badFormats.isEmpty())
					{
						BUILDER.append("\nDetected " + badFormats.size() + " improperly formatted lines!\n");
						BUILDER.append(badFormats);
					}
					System.out.println(BUILDER);
					if (outputPath != null)
						System.out.println("Exported to: " + Files.write(Paths.get(outputPath.toString(), "checksum_report.log"), BUILDER.toString().getBytes()));
				}
				else
					cancel();
			}
			else
			{
				System.out.println("Recogized path as: " + inputPath + " and algorithm as: " + digest.getAlgorithm() + (outputPath == null ? " and no output path" : " with the output path: " + outputPath) + ", continue? (boolean)");
				if (Boolean.parseBoolean(reader.readLine()))
				{
					System.out.println("Starting checksum of path: " + inputPath);
					try (final DirectoryStream<Path> fileStream = Files.newDirectoryStream(inputPath))
					{
						calculateFromFiles(fileStream, verbose);
					}
					FILE_CHECKSUMS.forEach(c -> c.addToBuilder(BUILDER));
					System.out.println();
					System.out.println("Finished:");
					System.out.println(BUILDER);
					if (outputPath != null)
						System.out.println("Exported to: " + Files.write(Paths.get(outputPath.toString(), "checksum." + EXTENSION_SUPPLIER.get()), BUILDER.toString().getBytes()));
				}
				else
					cancel();
			}
		} catch (UnrecognizedOptionException e)
		{
			System.err.println(e.getMessage());
			System.exit(1);
		} catch (MissingOptionException | MissingArgumentException e)
		{
			System.err.println(e.getMessage());
			System.exit(2);
		} catch (AlreadySelectedException e)
		{
			System.err.println(e.getMessage());
			System.exit(3);
		} catch (Exception e)
		{
			System.err.println("Unable to complete execution, caught exception: " + e + '.');
			e.printStackTrace();
			System.exit(10);
		}
	}

	private static void calculateFromFiles(DirectoryStream<Path> files, boolean verbose) throws IOException
	{
		for (Path file : files)
		{
			if (Files.isDirectory(file))
			{
				if (verbose)
					System.out.println("Found directory at: " + file);
				try (final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(file))
				{
					calculateFromFiles(directoryStream, verbose);
				}
				continue;
			}
			final byte[] hashBytes = getFileChecksum(file);
			final String hexHash = bytesToHex(hashBytes);
			if (verbose)
			{
				System.out.println("Found file at: " + file);
				System.out.println("Checksum calculated as: " + hexHash);
			}
//			BUILDER.append(hexHash).append("  ").append(file.getCanonicalPath()).append('\n');
			FILE_CHECKSUMS.add(new FileChecksum(file, hashBytes, digest.getAlgorithm()));
		}
	}
	
	protected static String bytesToHex(byte[] bytes)
	{
		final StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
		{
			final String hex = Integer.toHexString(b & 0xff);
			if (hex.length() == 1)
				builder.append('0');
			builder.append(hex);
		}
		return builder.toString();
	}
	
	private static byte[] getFileChecksum(Path path) throws IOException
	{
		try (final InputStream inputStream = Files.newInputStream(path))
		{
			while (inputStream.available() > 0)
				digest.update((byte) inputStream.read());
			
			return digest.digest();
		}
	}
	
	private static void cancel()
	{
		System.out.println("Cancelling execution...");
		System.exit(0);
	}
	
	protected static MessageDigest getDigest()
	{
		return digest;
	}

}
