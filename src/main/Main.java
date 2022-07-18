package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
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
		OPTIONS.addOption("h", "help", false, "Print the help screen.");
		OPTIONS.addOption("c", "check", false, "Check and verify a checksum file instead of generating one.");
		OPTIONS.addOption(Option.builder("a").longOpt("algorithm").desc("The algorithm to use for the checksum. Defaults to SHA-256.").argName("algorithm").optionalArg(true).required().build());
		OPTIONS.addOption(Option.builder("p").longOpt("path").desc("The path to check files. Defaults to running path.").argName("check path").required().build());
		OPTIONS.addOption("v", "verbose", false, "Verbose printing of the progress. Ideally should only be used when exporting the result, since the printing may be lengthy.");
		OPTIONS.addOption(Option.builder("e").longOpt("export").desc("Export path of the checksums.").argName("export path").optionalArg(true).build());
	}
	private static Path path = Paths.get("");
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
			if (commandLine.hasOption('p'))
				path = Paths.get(commandLine.getOptionValue('p', ""));
			if (commandLine.hasOption('a'))
				digest = MessageDigest.getInstance(commandLine.getOptionValue('a', "SHA-256").toUpperCase());
			else
				digest = MessageDigest.getInstance("SHA-256");
			if (commandLine.hasOption('e'))
				outputPath = Paths.get(commandLine.getOptionValue('e'));
			final boolean verbose = commandLine.hasOption('v');
			if (commandLine.hasOption('c'))
			{
				System.out.println("Recognized path as: " + path + " and algorithm as: " + digest.getAlgorithm() + ", continue? (boolean)");
				if (Boolean.parseBoolean(reader.readLine()))
				{
					System.out.println("Starting check of checksum file...");
					final ArrayList<Path> failedPaths = new ArrayList<Path>();
					final ArrayList<Path> missingPaths = new ArrayList<Path>();
					final ArrayList<String> badFormats = new ArrayList<String>();
					try (final Stream<String> stream = Files.lines(path))
					{
						final Iterator<String> iterator = stream.iterator();
						while (iterator.hasNext())
						{
							final StringBuilder checksumLine = new StringBuilder(iterator.next());
							try
							{
								final int breakIndex = checksumLine.indexOf("  ");
								final String storedHash = checksumLine.substring(0, breakIndex);
								final Path filePath = Paths.get(checksumLine.substring(breakIndex + 2));
								try
								{
									final byte[] hashBytes = digest.digest(Files.readAllBytes(filePath));
									final String hexHash = bytesToHex(hashBytes);
									final boolean success = hexHash.equals(storedHash), valid = hexHash.length() == storedHash.length();
									if (verbose)
										System.out.println(filePath.toString() + ' ' + (success ? "OK" : "FAILED"));
									if (verbose && !valid)
										System.err.println("Wrong algorithm used!");
									if (!success)
										failedPaths.add(filePath);
								} catch (FileNotFoundException e)
								{
									System.err.println(filePath.toString() + ' ' + "NOT FOUND");
									missingPaths.add(filePath);
								}
							} catch (StringIndexOutOfBoundsException e)
							{
								if (verbose)
									System.err.println("Improperly formatted line detected: " + checksumLine);
								badFormats.add(checksumLine.toString());
							}
						}
					}
					if (failedPaths.isEmpty())
						System.out.println("No failed checksums detected.");
					else
					{
						System.err.println("Detected " + failedPaths.size() + "failed files!");
						System.err.println(failedPaths);
					}
					if (!missingPaths.isEmpty())
					{
						System.err.println("Detected " + missingPaths.size() + " missing files!");
						System.err.println(missingPaths);
					}
					if (!badFormats.isEmpty())
					{
						System.err.println("Detected " + badFormats.size() + " improperly formatted lines!");
						System.err.println(badFormats);
					}
				}
				else
					cancel();
			}
			else
			{
				System.out.println("Recogized path as: " + path + " and algorithm as: " + digest.getAlgorithm() + (outputPath == null ? " and no output path" : " with the output path: " + outputPath) + ", continue? (boolean)");
				if (Boolean.parseBoolean(reader.readLine()))
				{
					final File checkPath = path.toFile();
					System.out.println("Starting checksum of path: " + checkPath);
					calculateFromFiles(checkPath.listFiles(), verbose);
					FILE_CHECKSUMS.forEach(c -> c.addToBuilder(BUILDER));
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
			System.err.println("Unrecognized option submitted: " + e.getOption());
		} catch (Exception e)
		{
			System.err.println("Unable to complete execution, caught exception: " + e + '.');
			e.printStackTrace();
			System.exit(10);
		}
	}
	
	private static void calculateFromFiles(File[] files, boolean verbose) throws IOException
	{
		for (File file : files)
		{
			if (file.isDirectory())
			{
				if (verbose)
					System.out.println("Found directory at: " + file.getCanonicalPath());
				calculateFromFiles(file.listFiles(), verbose);
				continue;
			}
			final byte[] fileBytes = Files.readAllBytes(file.toPath());
			final byte[] hashBytes = digest.digest(fileBytes);
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
