package de.codesourcery.toyprofiler;

public interface AgentRemoteControlMBean
{
	public void startProfiling();
	public void stopProfiling();
	public boolean isProfiling();
}