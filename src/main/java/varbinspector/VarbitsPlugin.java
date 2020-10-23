/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * Modified by andmcadams
 */
package varbinspector;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Varbits;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.VarbitComposition;
import net.runelite.api.IndexDataBase;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@PluginDescriptor(
	name = "Varbits"
)
public class VarbitsPlugin extends Plugin
{

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private static final int VARBITS_ARCHIVE_ID = 14;

	private int tick = 0;
	private int[] oldVarps = null;

	private Vector<VarbitUpdate> updatesToPush;

	private Multimap<Integer, Integer> varbits;

	@Getter
	private String session;

	private String SETUUID_COMMAND = "setuuid";
	private String GETUUID_COMMAND = "getuuid";
	private String START_COMMAND = "startrec";
	private String STOP_COMMAND = "stoprec";
	private Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

	private HttpClient httpClient = HttpClient.newHttpClient();

	private ReadWriteLock isRunningLock = new ReentrantReadWriteLock();
	private boolean isRunning = false;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Varbits started!");
		session = UUID.randomUUID().toString();
		log.info("Session key is: " + session);
	}

	private void beginRecording()
	{
		isRunningLock.writeLock().lock();
		try {
			varbits = HashMultimap.create();
			updatesToPush = new Vector<>();

			if(oldVarps == null)
				oldVarps = new int[client.getVarps().length];

			System.arraycopy(client.getVarps(), 0, oldVarps, 0, oldVarps.length);
			clientThread.invoke(() -> {
				IndexDataBase indexVarbits = client.getIndexConfig();
				final int[] varbitIds = indexVarbits.getFileIds(VARBITS_ARCHIVE_ID);
				for (int id : varbitIds)
				{
					VarbitComposition varbit = client.getVarbit(id);
					if (varbit != null)
					{
						varbits.put(varbit.getIndex(), id);
					}
				}
			});

			this.isRunning = true;
		}
		finally {
			isRunningLock.writeLock().unlock();
		}
	}

	private void stopRecording()
	{
		isRunningLock.writeLock().lock();
		try
		{
			isRunning = false;
			oldVarps = null;
			// Generate a new session key
			session = UUID.randomUUID().toString();
		}
		finally
		{
			isRunningLock.writeLock().unlock();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Varbits stopped!");
		stopRecording();
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		String command = commandExecuted.getCommand();
		String[] args = commandExecuted.getArguments();

		if (command.equals(SETUUID_COMMAND))
		{
			isRunningLock.readLock().lock();
			try
			{
				if (!isRunning)
				{
					log.info("setuuid command");
					if (args.length > 0)
						session = args[0];
					else
						log.warn("setuuid called without a String argument. UUID not set.");
				}
				else
					log.info("setuuid cannot be called while recording varbits.");
			}
			finally
			{
				isRunningLock.readLock().unlock();
			}
		}
		else if(command.equals(GETUUID_COMMAND))
		{
			log.info("getuuid command");
			// Put the UUID in the clipboard
			StringSelection stringSelection = new StringSelection(session);
			clipboard.setContents(stringSelection, null);
		}
		else if(command.equals(START_COMMAND))
		{
			log.info("startrec command");
			this.beginRecording();
		}
		else if(command.equals(STOP_COMMAND))
		{
			log.info("stoprec command");
			this.stopRecording();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		isRunningLock.readLock().lock();
		try
		{
			if (isRunning)
			{
				int index = varbitChanged.getIndex();
				int[] varps = client.getVarps();

				for (int i : varbits.get(index))
				{
					int old = client.getVarbitValue(oldVarps, i);
					int neew = client.getVarbitValue(varps, i);
					if (old != neew)
					{
						String name = Integer.toString(i);
						for (Varbits varbit : Varbits.values())
						{
							if (varbit.getId() == i)
							{
								name = String.format("%s(%d)", varbit.name(), i);
								break;
							}
						}
						// Might not want to call getTickCount over and over since it can probably be done once.
						// Using tick is potentially unsafe but probably won't make too much of a difference.
						log.info("Pushing out a new update!");
						updatesToPush.add(new VarbitUpdate(i, name, old, neew, tick));
					}
				}
				System.arraycopy(client.getVarps(), 0, oldVarps, 0, oldVarps.length);
			}
		}
		finally
		{
			isRunningLock.readLock().unlock();
		}
	}


	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		// Update tick value.
		tick = client.getTickCount();
		// Clone the vector for sync safety.
		isRunningLock.readLock().lock();
		try
		{
			if (isRunning)
			{
				if (updatesToPush.size() > 0)
				{
					Vector<VarbitUpdate> updatesClone = (Vector) updatesToPush.clone();

					// Every game tick, push out all varbit updates.

					// Construct the params for the POST request.
					// Session needs to be uniquely generated
					String requestBody = "{\"session\": \"" + session + "\",\"info\":[";
					requestBody += StringUtils.join(updatesClone, ',');
					requestBody += "]}";

					// Construct the request.
					HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3001/updateMany")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

					// Send out async request.
					httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

					// Clear the updates that have been pushed.
					updatesToPush.removeAll(updatesClone);
				}
			}
		}
		finally
		{
			isRunningLock.readLock().unlock();
		}
	}
}
