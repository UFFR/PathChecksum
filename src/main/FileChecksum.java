package main;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class FileChecksum implements Serializable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1593493925541749371L;
	private final Path file;
	private final byte[] hash;
	private final String algorithm;
	public FileChecksum(Path file, byte[] hash, String algorithm)
	{
		this.file = file;
		this.hash = hash;
		this.algorithm = algorithm;
	}
	
	/**
	 * @return the file
	 */
	public Path getFile()
	{
		return file;
	}

	/**
	 * @return the hash
	 */
	public byte[] getHash()
	{
		return hash.clone();
	}

	/**
	 * @return the algorithm
	 */
	public String getAlgorithm()
	{
		return algorithm;
	}

	public void addToBuilder(StringBuilder builder)
	{
		builder.append(Main.bytesToHex(getHash())).append("  ").append(getFile()).append('\n');
	}
	
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(hash);
		result = prime * result + Objects.hash(algorithm, file);
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
		return Objects.equals(algorithm, other.algorithm) && Objects.equals(file, other.file)
				&& Arrays.equals(hash, other.hash);
	}
	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("FileChecksum [file=").append(file).append(", hash=").append(Arrays.toString(hash))
				.append(", algorithm=").append(algorithm).append(']');
		return builder.toString();
	}

}
