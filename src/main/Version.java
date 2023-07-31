package main;

import java.io.Serializable;
import java.util.Objects;

public class Version implements Comparable<Version>, Serializable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1783596447573409981L;
	private final int major, minor, patch;
	private final char suffix;
	
	public Version(int major, int minor, int patch, char suffix)
	{
		if (major < 0)
			throw new IllegalArgumentException("The field [major] cannot be negative!");
		if (minor < 0)
			throw new IllegalArgumentException("The field [minor] cannot be negative!");
		if (patch < 0)
			throw new IllegalArgumentException("The field [patch] cannot be negative!");
		if (!Character.isLetter(suffix))
			throw new IllegalArgumentException("The field [suffix] must be a letter!");
		
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.suffix = Character.toLowerCase(suffix);
	}

	public Version(int major, int minor, int patch)
	{
		this(major, minor, patch, 'r');
	}
	
	public Version(int major, int minor)
	{
		this(major, minor, 0);
	}
	
	public Version(int major)
	{
		this(major, 0);
	}
	
	public Version()
	{
		this(0);
	}
	
	public int getMajor()
	{
		return major;
	}

	public int getMinor()
	{
		return minor;
	}


	public int getPatch()
	{
		return patch;
	}

	public char getSuffix()
	{
		return suffix;
	}

	@Override
	public int compareTo(Version other)
	{
		if (major != other.major)
			return major > other.major ? 1 : -1;
		else if (minor != other.minor)
			return minor > other.minor ? 1 : -1;
		else if (patch != other.patch)
			return patch > other.patch ? 1 : -1;
		else if (Character.toLowerCase(suffix) != Character.toLowerCase(other.suffix))
			return Character.toLowerCase(suffix) > Character.toLowerCase(other.suffix) ? 1 : -1;
		else
			return 0;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(major, minor, patch, suffix);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof Version))
			return false;
		final Version other = (Version) obj;
		return major == other.major && minor == other.minor && patch == other.patch && suffix == other.suffix;
	}
	
	@Override
	public String toString()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append('v').append(major).append('.').append(minor).append('.').append(patch).append(suffix);
		return builder.toString();
	}

	@Override
	public Version clone()
	{
		return new Version(major, minor, patch, suffix);
	}

}
