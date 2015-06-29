package the.maxx.luaheadshot;

import java.io.Serializable;

/** Holds state of current player: his position, orientation and last action */
public class ClientState implements Serializable
{
	private static final long serialVersionUID = -2827593955639386628L;

	public ClientState() {}
	public ClientState(int cid) {userId = cid;}
	public ClientState(Action act) {action = act;}

	public int userId = -1337;
	public double posX = 0.0, posY = 0.0, posZ = 0.0;
	public double rotX = 0.0, rotY = 0.0, rotZ = 0.0;

	/** Player action/state */
	public enum Action
	{
		CONNECTED,
		OPONNENT_DEAD,
		ENEMY_CONNECTED,
		DEAD, ALIVE,
		STUCK, //reset me, please
		MOVING, JUMPING, //optional
		MOVE_RIGHT, MOVE_FORWARD,
		MOVE_BACKWARD, MOVE_LEFT,
		SHOOTING,
	}
	public Action action = Action.DEAD;

	/** Create identical copy of object */
	public ClientState copy()
	{
		synchronized (this)
		{
			ClientState ret = new ClientState();
			ret.userId = this.userId;
			ret.action = this.action;

			ret.posX = this.posX;
			ret.rotX = this.rotX;

			ret.posY = this.posY;
			ret.rotY = this.rotY;

			ret.posZ = this.posZ;
			ret.rotZ = this.rotZ;

			return ret;
		}
	}

	/**
	 * Replaces inner coords, rotation and action with provided
	 * @param with replace with this data
	 * @return this
	 */
	public ClientState replace(ClientState with)
	{
		synchronized (this)
		{
			this.action = with.action;

			this.posX = with.posX;
			this.rotX = with.rotX;

			this.posY = with.posY;
			this.rotY = with.rotY;

			this.posZ = with.posZ;
			this.rotZ = with.rotZ;

			return this;
		}
	}

	public double distanceDiff(ClientState cs)
	{
		double ret = Math.pow(cs.posX, 2) + Math.pow(cs.posY, 2) + Math.pow(cs.posZ, 2);
		return Math.sqrt(ret);
	}
}
