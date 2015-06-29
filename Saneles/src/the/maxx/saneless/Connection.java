package the.maxx.saneless;

import org.jetbrains.annotations.NotNull;
import the.maxx.luaheadshot.ClientState;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

/** Socket manager: data send/recv */
public class Connection extends Thread
{
	final String destIp;
	Socket socket;
	BufferedInputStream inputStream;
	BufferedOutputStream outputStream;

	public Connection() throws IOException
	{
		super();
		byte[] ip = new byte[30];
		int len;
		len = System.in.read(ip);

		if (len > 25) throw new IOException("Invalid length");

		destIp = new String(ip, 0, len);

		socket = new Socket(destIp, 29176);
		inputStream = new BufferedInputStream(socket.getInputStream());
		outputStream = new BufferedOutputStream(socket.getOutputStream());
	}

	public void send(@NotNull ClientState state)
	{
		String str = "[" + state.userId + "|" + state.action +
			"|" + state.posX + "|" + state.posY +
			"|" + state.posZ + "|" + state.rotX +
			"|" + state.rotY + "|" + state.rotZ + "]";
		try
		{
			byte[] bt = str.getBytes();
			outputStream.write(bt, 0, bt.length);
			outputStream.flush();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		ClientState state = new ClientState();
		char c;
		String out, split[];

		while (true)
		{
			try
			{
				out = "";

				while ((c = (char) inputStream.read()) != ']')
					out = out + String.valueOf(c);

				out = out.substring(1, out.length() - 1);
				split = out.split("|");

				state.userId = Integer.valueOf(split[0]);
				state.action = ClientState.Action.valueOf(split[1]);
				state.posX = Double.valueOf(split[2]);
				state.posY = Double.valueOf(split[3]);
				state.posZ = Double.valueOf(split[4]);
				state.rotX = Double.valueOf(split[5]);
				state.rotY = Double.valueOf(split[6]);
				state.rotZ = Double.valueOf(split[7]);

				//TODO: handle
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}
