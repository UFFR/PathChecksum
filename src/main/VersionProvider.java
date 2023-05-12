package main;

import picocli.CommandLine.IVersionProvider;

public final class VersionProvider implements IVersionProvider
{

	@Override
	public String[] getVersion() throws Exception
	{
		return new String[] {"PathChecksum " + Main.VERSION, "jline v3.21.0", "picocli v4.7.3", "progressbar v0.9.5"};
	}

}
