package the.maxx.luaheadshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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

		public ClientHandler(TCPServer serv, Socket connectionSocket, int id) throws IOException
		{
			super();
			this.connectionSocket = connectionSocket;
			this.server = serv;
			this.idClient = id;
			this.nodeId = server.node.datahub.getNextFreeSlotId();
			if (this.nodeId <= 0)
				this.nodeId = -1 * id;
			if (this.nodeId > 0)
				state = server.node.datahub.moveToRespawnPoint(this.nodeId);
			else
				state = new ClientState();
			state.userId = id;
			try
			{
				inFromClient = new BufferedInputStream(connectionSocket.getInputStream());
				outToClient = new BufferedOutputStream(connectionSocket.getOutputStream());

				server.node.datahub.readWriteSync.lock();
				TCPServer.Clients.add(this);
				server.node.datahub.announceNewPlayer(this);
				this.start();
				//signal new data on list
				server.node.datahub.setAllAsNew();
				server.node.datahub.readWriteSync.unlock();

				Log.Info("New client connected, id:" + nodeId + "@" + connectionSocket.getInetAddress());
			}
			catch (IOException e)
			{
				Log.Exception("Client socket init failed", e);
				connectionSocket.close();
			}
		}

		//client run loop
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
					out = "";

					while ((c = (char) inFromClient.read()) != closeCharacter)
						out = out + String.valueOf(c);

					if (nodeId < 0)
						return;

					out = out.substring(1, out.length() - 1);

					splittedOut = out.split("|");

					synchronized (state)
					{
						state.userId = Integer.valueOf(splittedOut[0]);
						state.action = ClientState.Action.valueOf(splittedOut[1]);
						state.posX = Double.valueOf(splittedOut[2]);
						state.posY = Double.valueOf(splittedOut[3]);
						state.posZ = Double.valueOf(splittedOut[4]);
						state.rotX = Double.valueOf(splittedOut[5]);
						state.rotY = Double.valueOf(splittedOut[6]);
						state.rotZ = Double.valueOf(splittedOut[7]);

						node.datahub.newStateFromId(nodeId, state);
					}
				}
				catch (IOException e)
				{ //socket borked
					Log.Warning("Connection lost for socket id " + this.idClient);
					break;
				}
			}
			this.finish();
		}

		//region sendStatus
		public void sendStatus(ClientState state, ClientState.Action actionOverride) throws IOException
		{
			sendString("[" + state.userId + "|" + actionOverride +
				"|" + state.posX + "|" + state.posY +
				"|" + state.posZ + "|" + state.rotX +
				"|" + state.rotY + "|" + state.rotZ + "]");
		}

		public void sendStatus(ClientState st) throws IOException
		{
			sendString("[" + st.userId + "|" + st.action +
				"|" + st.posX + "|" + st.posY +
				"|" + st.posZ + "|" + st.rotX +
				"|" + st.rotY + "|" + st.rotZ + "]");
		}

		public void sendString(String str) throws IOException
		{
			if (connectionSocket.isClosed())
				return;
			try
			{
				byte[] bt = str.getBytes();
				outToClient.write(bt, 0, bt.length);
				outToClient.flush();
			}
			catch (Exception e)
			{
				Log.Exception(e);
			}
		}
		//endregion

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
			TCPServer.Clients.remove(this);
		}
	}
}
