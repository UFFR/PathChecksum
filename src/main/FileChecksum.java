package main;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class FileChecksum implements Serializable
{
	private static final long serialVersionUID = -4930384739891546680L;
	private final Path file;
	private final String hash, algorithm;
	private final byte[] bytes;
	
	public FileChecksum(Path file, byte[] hash, String algorithm)
	{
		this.file = file;
		this.bytes = hash;
		this.hash = Main.bytesToHex(true, hash);
		this.algorithm = algorithm;
	}
	
	public FileChecksum(Path file, byte[] bytes, String hash, String algorithm)
	{
		this.file = file;
		this.bytes = bytes;
		this.hash = hash;
		this.algorithm = algorithm;
	}
	
	public Path getFile()
	{
		return file;
	}

	public String getHash()
	{
		return hash;
	}

	public String getAlgorithm()
	{
		return algorithm;
	}
	
	public byte[] getBytes()
	{
		return bytes.clone();
	}

	public void addToBuilder(StringBuilder builder, Path inputPath, boolean absolutePaths)
	{
		builder.append(getHash()).append("  ").append(absolutePaths
				? getFile().toAbsolutePath() : getFile().startsWith(inputPath) && !getFile().equals(inputPath)
						? inputPath.relativize(getFile()) : getFile()).append('\n');
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(bytes);
		result = prime * result + Objects.hash(algorithm, file, hash);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof FileChecksum))
			return false;
		final FileChecksum other = (FileChecksum) obj;
		return Objects.equals(algorithm, other.algorithm) && Arrays.equals(bytes, other.bytes)
				&& Objects.equals(file, other.file) && Objects.equals(hash, other.hash);
	}

	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("FileChecksum [file=").append(file).append(", hash=").append(hash).append(", algorithm=")
				.append(algorithm).append(", bytes=").append(Arrays.toString(bytes)).append(']');
		return builder.toString();
	}

}
