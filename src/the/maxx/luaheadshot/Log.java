package the.maxx.luaheadshot;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Arrays;

/** Logger, passes messages to root node for output */
public final class Log
{
	public static final class Message implements Serializable
	{
		private static final long serialVersionUID = 1977681904500178161L;

		public int myRank;
		public String severity;
		public String message;

		public Message() {}

		public Message(@NotNull String s, @NotNull String m)
		{
			myRank = MpiNode.Me() == null ? -1 : MpiNode.Me().rank;
			severity = s;
			message = m;
		}

		public Message(int r, @NotNull String s, @NotNull String m)
		{
			myRank = r;
			severity = s;
			message = m;
		}

		@Override
		@Contract(pure = true)
		public String toString()
		{
			return "node#" + myRank + " " + severity + ": " + message;
		}
	}

	private static int GetRank()
	{
		return MpiNode.Me() == null ? -1 : MpiNode.Me().rank;
	}

	public static void PrintError(Message e)
	{
		if (Main.IS_DEBUG || GetRank() <= 0)
		{
			if (e.severity.startsWith("e"))
				System.err.println(e);
			else
				System.out.println(e);
		}
		else
			MpiNode.Me().icomm.SendToRoot(e);
	}

	public static void Error(String s)
	{
		PrintError(new Message("error", s));
	}

	public static void Exception(Exception e)
	{
		PrintError(new Message("exception", e.toString()));
	}

	public static void Exception(String msg, Exception e)
	{
		PrintError(
			new Message(
				"exception",
				msg + ": " + e.toString() + "\n" + Arrays.toString(e.getStackTrace())
			)
		);
	}

	public static void Warning(String s)
	{
		PrintError(new Message("ewarning", s));
	}

	public static void Info(String s)
	{
		PrintError(new Message("info", s));
	}

	public static void Debug(String s)
	{
		if (Main.IS_DEBUG) PrintError(new Message("debug", s));
	}
}
