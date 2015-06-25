package the.maxx.luaheadshot;

import mpi.MPI;
import mpi.MPIException;


public final class MpiNode
{
	public static MpiNode Me()
	{
		if (MPI.COMM_WORLD.Rank() >= 0)
			return Datahub.AllNodes[MPI.COMM_WORLD.Rank()];
		else
			throw new RuntimeException("MPI not initialized");
	}

	private boolean initialized = false;
	private boolean finishing = false;

	public final int rank;
	public final int size;

	final ThePlayer manager;
	public final Datahub datahub;
	public final MpiIntercom icomm;

	/** Init, pass main's args as parameter */
	public MpiNode(String[] args)
	{
		if (!MPI.Initialized())
			MPI.Init(args);

		rank = MPI.COMM_WORLD.Rank();
		size = MPI.COMM_WORLD.Size();

		if (rank == 0 && size < 2)
		{
			MPI.COMM_WORLD.Abort(402);
			Log.Error("Invalid cluster size; at least three nodes should be present");
		}

		Datahub.AllNodes[rank] = this;

		icomm = new MpiIntercom(this);

		MPI.Buffer_attach(icomm.Buffer);

		if (rank == 0) //node #0 manages whole game
		{
			TCPServer.ServerInstance = new TCPServer(this);
			TCPServer.ServerInstance.start();

			datahub = new Datahub(this);
			datahub.start();

			manager = new ThePlayerSudo(this);
		}
		else
		{
			manager = new ThePlayer(this);
			datahub = null;
		}

		Log.Info("Node initialized");
		initialized = true;
	}

	public boolean isUsable() { return MPI.Initialized() && this.initialized; }
	public boolean isFinishing() { return finishing; }
	public void setFinishing() { finishing = true; }

	public void run()
	{
		if (!isUsable())
			return;

		icomm.Barrier();

		do
		{
			try
			{
				manager.run();
			}
			catch (MPIException e)
			{
				manager.reset();
				Log.Exception("ThePlayer error, retrying", e);
			}
		} while (!this.finishing);

		icomm.Barrier();
	}

	public void finish()
	{
		Log.Error("Node abort");
		if (TCPServer.ServerInstance != null)
			TCPServer.ServerInstance.finish();

		if (!isUsable())
			return;
		manager.finish();
		MPI.COMM_WORLD.Abort(500);
	}
}
