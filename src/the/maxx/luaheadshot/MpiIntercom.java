package the.maxx.luaheadshot;

import mpi.MPI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * MpiIntercom - wrapper for communication between instances of MPI
 * @author theMaxx
 */
public class MpiIntercom
{
	/** Buffer length in bytes */
	static final int BUFFER_LENGTH = 4000;
	final ByteBuffer Buffer =
		ByteBuffer.allocateDirect(
			MpiIntercom.BUFFER_LENGTH
				+ (MPI.SEND_OVERHEAD > MPI.BSEND_OVERHEAD ?
					MPI.SEND_OVERHEAD : MPI.BSEND_OVERHEAD)
		);


	/** Secure random generator, initialized in {@link Main#main} */
	static SecureRandom Random = new java.security.SecureRandom();
	/**
	 * Generate random message tag
	 * @return 1..MAX_INT
	 */
	static int GenerateTag()
	{
		return Random.nextInt(Integer.MAX_VALUE - 2) + 1;
	}

	MpiNode node;

	public MpiIntercom(MpiNode n)
	{
		node = n;
	}

	/**
	 * Deserialize given byte array into object
	 * @param in byte array
	 * @return deserialized object; null on failure
	 */
	@Nullable
	private static MpiMessage Deserialize(@NotNull byte[] in)
	{
		try
		{
			ByteArrayInputStream bis = new ByteArrayInputStream(in);
			ObjectInputStream iput = new ObjectInputStream(bis);
			return (MpiMessage) iput.readObject();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Serialize given object into bytes array
	 * @param msg Message object
	 * @return serialized object; null on failure
	 */
	@Nullable
	private static byte[] Serialize(@NotNull MpiMessage msg)
	{
		try
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream oout = new ObjectOutputStream(bos);
			oout.writeObject(msg);
			return bos.toByteArray();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Check (non-blockingly) if there are any messages waiting
	 * and receive (blockingly) them
	 * @return List of all messages received (possibly empty)
	 */
	public List<MpiMessage> ReceiveAll()
	{
		ArrayList<MpiMessage> ret = new ArrayList<>();
		mpi.Status stat;
		byte[] fetch;
		MpiMessage msg;

		while ((stat = MPI.COMM_WORLD.Iprobe(MPI.ANY_SOURCE, MPI.ANY_TAG)) != null)
		{
			if (stat.Get_count(MPI.BYTE) <= 10) //too small to be an object
				continue;

			fetch = new byte[BUFFER_LENGTH];

			stat = MPI.COMM_WORLD.Recv(fetch, 0, BUFFER_LENGTH, MPI.BYTE, MPI.ANY_SOURCE, MPI.ANY_TAG);

			if (stat.Get_count(MPI.BYTE) <= 10) //too small to be an object
				continue;

			msg = Deserialize(fetch);
			if (msg == null)
			{
				Log.Warning("Failed to deserialize message object");
				continue;
			}

			switch (msg.messageType)
			{
				case LOG_MESSAGE:
					Log.PrintError((Log.Message)msg.data);
					continue; //continue while loop
			}

			ret.add(msg);
		}
		return ret;
	}

	/**
	 * Send message to specified node number
	 * @param msg Message
	 * @param nodeId Node identifier
	 * @return success
	 */
	public boolean SendTo(@NotNull MpiMessage msg, int nodeId)
	{
		if (nodeId < 0)
			return false;
		msg.fromRank = node.rank;
		byte[] buf = Serialize(msg);
		if (buf == null)
			return false;

		MPI.COMM_WORLD.Isend(buf, 0, buf.length, MPI.BYTE, nodeId, GenerateTag());
		return true;
	}

	/**
	 * Send message to node #0
	 * Same rules as for {@link #SendTo(MpiMessage, int)} apply
	 * @param msg Message
	 * @return success
	 */
	public boolean SendToRoot(@NotNull MpiMessage msg)
	{
		return SendTo(msg, 0);
	}

	public boolean SendToRoot(MpiMessage.Type status) {
		MpiMessage msg = new MpiMessage();
		msg.messageType = status;
		return SendTo(msg, 0);
	}

	/**
	 * Send message to node #0
	 * @see #SendToRoot(MpiMessage)
	 * @param lmsg Message to send
	 * @return success
	 */
	public boolean SendToRoot(@NotNull Log.Message lmsg)
	{
		MpiMessage msg = new MpiMessage();
		msg.messageType = MpiMessage.Type.LOG_MESSAGE;
		lmsg.myRank = node.rank;
		msg.data = lmsg;
		return SendToRoot(msg);
	}

	/**
	 * Iteratively send a message to all nodes, excluding yourself
	 * @param msg Message to send
	 * @return true on success; false if any single sending failed
	 */
	public boolean SendToAll(@NotNull MpiMessage msg)
	{return SendToAll(msg, false);}

	/**
	 * Iteratively send a message to all nodes, excluding yourself
	 * @param msg Message to send
	 * @param incSelf Include your own node
	 * @return true on success; false if any single sending failed
	 */
	public boolean SendToAll(@NotNull MpiMessage msg, boolean incSelf)
	{
		boolean ret = true;
		for (int i = 0; i < node.size; i++)
		{
			if (i == node.rank && !incSelf)
				continue;
			if (!SendTo(msg, i))
				ret = false;
		}
		return ret;
	}

	/**
	 * Send message as broadcast
	 * One should first either announce there is broadcast ahead
	 * @param msg Message
	 * @return success
	 */
	public boolean BroadcastSend(@NotNull MpiMessage msg)
	{
		msg.fromRank = node.rank;
		byte[] buf = Serialize(msg);
		if (buf == null)
		{
			Log.Warning("Failed to serialize message");
			return false;
		}
		MPI.COMM_WORLD.Bcast(buf, 0, buf.length, MPI.BYTE, msg.fromRank);
		return true;
	}

	/**
	 * Receive broadcast message
	 * @return message or null on failure
	 */
	@Nullable
	public MpiMessage BroadcastRecv()
	{
		byte[] buf = new byte[BUFFER_LENGTH];
		MPI.COMM_WORLD.Bcast(buf, 0, BUFFER_LENGTH, MPI.BYTE, MPI.ANY_SOURCE);
		return Deserialize(buf);
	}

	/** Binding of {@link mpi.MPI#COMM_WORLD#Barrier()} */
	public void Barrier()
	{
		MPI.COMM_WORLD.Barrier();
	}

	/**
	 * Signal abonormal cluster shutdown
	 * @param reason optional reason for shutdown
	 */
	public void SignalAbort(@Nullable String reason)
	{
		if (node == null)
		{
			System.out.println("Aborting: " + reason);
			if (MPI.Initialized())
				MPI.COMM_WORLD.Abort(500);
			return;
		}

		MpiMessage msg = new MpiMessage();
		msg.data = reason;
		msg.messageType = MpiMessage.Type.ABORT_EXECUTION;
		SendToAll(msg);
	}

}
