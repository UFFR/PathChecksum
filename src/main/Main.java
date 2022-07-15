package main;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.function.Supplier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

public class Main
{
	private static final Options OPTIONS = new Options();
	private static final CommandLineParser PARSER = new DefaultParser();
	private static final HelpFormatter HELP_FORMATTER = new HelpFormatter();
	private static final StringBuilder BUILDER = new StringBuilder();
	static
	{
		OPTIONS.addOption("h", false, "Print the help screen.");
		OPTIONS.addOption("a", true, "The algorithm to use for the checksum. Defaults to SHA-256.");
		OPTIONS.addOption("p", true, "The path to check files. Defaults to running path.");
		OPTIONS.addOption("v", false, "Verbose printing of the progress. Ideally should only be used when exporting the result, since the printing may be lengthy.");
		OPTIONS.addOption("e", true, "Export path of the checksums.");
	}
	private static Path path = Paths.get("");
	private static File outputFile;
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
			final CommandLine commandLine = PARSER.parse(OPTIONS, args);
			if (commandLine.hasOption('h'))
			{
				HELP_FORMATTER.printHelp("Requires specified algorithm and path, with optional export path.", OPTIONS);
				System.exit(0);
			}
			if (commandLine.hasOption('p'))
				path = Paths.get(commandLine.getOptionValue('p', ""));
			if (commandLine.hasOption('a'))
				digest = MessageDigest.getInstance(commandLine.getOptionValue('a', "SHA-256").toUpperCase());
			else
				digest = MessageDigest.getInstance("SHA-256");
			if (commandLine.hasOption('e'))
				outputFile = new File(commandLine.getOptionValue('e'));
			
			System.out.println("Recogized path as: " + path + " and algorithm as: " + digest.getAlgorithm() + (outputFile == null ? " and no output path" : " with the output path: " + outputFile) + ", continue? (boolean)");
			final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			final boolean accepted = Boolean.parseBoolean(reader.readLine());
			if (accepted)
			{
				final File checkPath = path.toFile();
				System.out.println("Starting checksum of path: " + checkPath);
				calculateFromFiles(checkPath.listFiles(), commandLine.hasOption('v'));
				System.out.println("Finished:");
				System.out.println(BUILDER);
				if (outputFile != null)
					System.out.println("Exported to: " + Files.write(Paths.get(outputFile.getCanonicalPath(), "checksum." + EXTENSION_SUPPLIER.get()), BUILDER.toString().getBytes()));
			}
			else
			{
				System.out.println("Cancelling execution...");
				System.exit(0);
			}
		} catch (Exception e)
		{
			System.out.println("Unable to complete execution, caught exception: " + e + '.');
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
			BUILDER.append(hexHash).append("  ").append(file.getCanonicalPath()).append('\n');
		}
	}
	
	private static String bytesToHex(byte[] bytes)
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

}
