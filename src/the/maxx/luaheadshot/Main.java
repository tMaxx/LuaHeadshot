package the.maxx.luaheadshot;

import the.maxx.luaheadshot.util.ShutdownHandler;

/** Entrypoint for application */
public class Main
{
	/** Switch off to disable debug messages */
	public static final boolean IS_DEBUG = true;

	public static void main(String[] args)
	{
		System.out.println("Random int: " + Integer.toString(MpiIntercom.Random.nextInt()));

		ShutdownHandler.Install();

		try
		{
			new MpiNode(args).run();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			MpiNode.Me().icomm.SignalAbort(e.toString() + " " + e.getMessage());
		}
		finally { MpiNode.Me().finish(); }
	}
}
