package main;

import java.security.Security;
import java.util.Iterator;

public final class AlgorithmCandidates implements Iterable<String>
{

	@Override
	public Iterator<String> iterator()
	{
		return Security.getAlgorithms("MessageDigest").iterator();
	}

}
