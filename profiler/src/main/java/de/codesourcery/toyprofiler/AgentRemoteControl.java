package de.codesourcery.toyprofiler;

final class AgentRemoteControl implements AgentRemoteControlMBean
{
	@Override
	public void startProfiling() {
		Agent.startProfiling();
	}

	@Override
	public void stopProfiling() {
		Agent.stopProfiling();
	}

	@Override
	public boolean isProfiling()
	{
		return Agent.isProfiling();
	}
}