package the.maxx.luaheadshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TCPServer extends Thread
{
	static final char closeCharacter = ']';

	static boolean ServerRunning = true;
	static TCPServer ServerInstance = null;

	public static final List<ClientHandler> Clients = new CopyOnWriteArrayList<>();

	AtomicInteger playerId = new AtomicInteger();
	ServerSocket welcomeSocket;
	MpiNode node;

	public TCPServer(MpiNode node)
	{
		super();
		this.node = node;
	}

	@Override
	public void run()
	{
		try
		{
			welcomeSocket = new ServerSocket(29176);
			if (welcomeSocket.isClosed())
				throw new IOException("Server socket already closed O.O");
			Log.Info("Server started @ " + welcomeSocket.getLocalPort());
		}
		catch (IOException e)
		{
			Log.Error("Failed to init server socket: " + e);
		}

		while (ServerRunning)
		{
			try
			{
				Socket connectionSocket = welcomeSocket.accept();
				//adds itself to queue
				new ClientHandler(this, connectionSocket, playerId.incrementAndGet());
			}
			catch (IOException e)
			{
				Log.Exception("New connection error", e);
			}
		}
	}

	public void finish()
	{
		TCPServer.ServerRunning = false;
		try
		{
			if (welcomeSocket != null)
				this.welcomeSocket.close();
		}
		catch (IOException ignore) {}

		for (ClientHandler ch : TCPServer.Clients)
		{
			try
			{
				ch.connectionSocket.close();
			}
			catch (IOException ignore) {}
		}
	}

	public class ClientHandler extends Thread
	{
		Socket connectionSocket;
		BufferedInputStream inFromClient;
		BufferedOutputStream outToClient;

		int idClient;
		int nodeId;
		final ClientState state;
		final TCPServer server;

		public ClientHandler(TCPServer serv, Socket connectionSocket, int id)
		{
			super();
			this.connectionSocket = connectionSocket;
			this.server = serv;
			this.idClient = id;
			this.nodeId = server.node.datahub.getNextFreeSlotId();
			if (this.nodeId <= 0)
				this.nodeId = -1 * id;
			state = new ClientState();
			state.userId = id;
			try
			{
				inFromClient = new BufferedInputStream(connectionSocket.getInputStream());
				outToClient = new BufferedOutputStream(connectionSocket.getOutputStream());
				this.sendString("[" + id + "|" + ClientState.Action.CONNECTED + "|" + 0 + "|" +
					0 + "|" + 0 + "|" + 0 + "|" + 0 + "|" + 0 + "]");
				TCPServer.Clients.add(this);
				this.start();
				Log.Info("New client connected, id:" + nodeId + "@" + connectionSocket.getInetAddress());
			}
			catch (IOException e)
			{
				Log.Exception("Client socket init failed", e);
			}
		}

		@Override
		public void run()
		{
			char c;
			String out;
			String[] splittedOut;

			while (TCPServer.ServerRunning && !connectionSocket.isClosed())
			{
				try
				{
					inFromClient = new BufferedInputStream(connectionSocket.getInputStream());

					out = "";

					while ((c = (char) inFromClient.read()) != closeCharacter)
						out = out + String.valueOf(c);

					out = out.substring(1, out.length() - 1);

					splittedOut = out.split("|");

					synchronized (state)
					{
						state.action = ClientState.Action.valueOf(splittedOut[0]);
						state.posX = Double.valueOf(splittedOut[1]);
						state.posY = Double.valueOf(splittedOut[2]);
						state.posZ = Double.valueOf(splittedOut[3]);
						state.rotX = Double.valueOf(splittedOut[4]);
						state.rotY = Double.valueOf(splittedOut[5]);
						state.rotZ = Double.valueOf(splittedOut[6]);
					}

					if (nodeId > 0)
						node.datahub.updateStateForId(nodeId, state);
				}
				catch (IOException e)
				{ //socket borked
					Log.Warning("Connection lost for socket id " + this.idClient);
					break;
				}
			}
			this.finish();
		}

		public void sendMyUpdatedPositionToAll(ClientState update)
		{
			if (connectionSocket.isClosed())
				return;

			synchronized (state)
			{
				state.replace(update);
			}
			for (ClientHandler ch : TCPServer.Clients)
			{
				try
				{
					ch.sendString(
						"[" + this.nodeId + "|" + update.action +
							"|" + update.posX + "|" + update.posY +
							"|" + update.posZ + "|" + update.rotX +
							"|" + update.rotY + "|" + update.rotZ + "]"
					);
				}
				catch (IOException e)
				{
					Log.Exception("Send failed", e);
				}
			}

		}

		public void sendString(String str) throws IOException
		{
			if (connectionSocket.isClosed())
				return;
			try
			{
				ByteArrayOutputStream bytestream;
				bytestream = new ByteArrayOutputStream(str.length());

				DataOutputStream out;
				out = new DataOutputStream(bytestream);

				for (int i = 0; i < str.length(); i++)
					out.write((byte) str.charAt(i));

				outToClient.write(bytestream.toByteArray(), 0, bytestream.size());
				outToClient.flush();

				outToClient = new BufferedOutputStream(connectionSocket.getOutputStream());
			}
			catch (Exception e)
			{
				Log.Exception(e);
			}
		}

		public void finish()
		{
			try
			{
				if (!connectionSocket.isClosed())
					connectionSocket.close();
			}
			catch (IOException ignore) {}
			if (nodeId > 0)
				node.datahub.setSlotAsFree(nodeId);
			synchronized (TCPServer.Clients)
			{
				TCPServer.Clients.remove(this);
			}
		}
	}
}
