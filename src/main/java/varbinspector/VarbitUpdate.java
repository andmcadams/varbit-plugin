package varbinspector;

import lombok.Getter;

public class VarbitUpdate
{
	@Getter
	private int varbitNumber;

	@Getter
	private String name;

	@Getter
	private int old;

	@Getter
	private int neew;

	@Getter
	private int tick;

	public VarbitUpdate(int varbitNumber, String name, int old, int neew, int tick)
	{
		this.varbitNumber = varbitNumber;
		this.name = name;
		this.old = old;
		this.neew = neew;
		this.tick = tick;
	}

	@Override
	public String toString()
	{
		return "{\"index\":" + varbitNumber + ",\"oldValue\":" + old + ",\"newValue\":" + neew + ",\"tick\":" + tick + "}";
	}
}
