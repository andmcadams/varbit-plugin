package varbinspector;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Provides;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Vector;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.VarbitComposition;
import net.runelite.api.IndexDataBase;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
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
	private VarbitsConfig config;

	@Inject
	private ClientThread clientThread;

	private static final int VARBITS_ARCHIVE_ID = 14;

	private int tick = 0;
	private int[] oldVarps = null;

	private Vector<VarbitUpdate> updatesToPush;

	private Multimap<Integer, Integer> varbits;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Varbits started!");
		varbits = HashMultimap.create();

		if(oldVarps == null)
			oldVarps = new int[client.getVarps().length];

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

		updatesToPush = new Vector<>();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Varbits stopped!");
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		int index = varbitChanged.getIndex();
		int[] varps = client.getVarps();

		for (int i : varbits.get(index))
		{
			int old = client.getVarbitValue(oldVarps, i);
			int neew = client.getVarbitValue(varps, i);
			if (old != neew)
			{
				client.setVarbitValue(oldVarps, i, neew);

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

	}

	private HttpClient httpClient = HttpClient.newHttpClient();

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		// Update tick value.
		tick = client.getTickCount();

		// Clone the vector for sync safety.
		if (updatesToPush.size() > 0)
		{
			Vector<VarbitUpdate> updatesClone = (Vector) updatesToPush.clone();

			// Every game tick, push out all varbit updates.

			// Construct the params for the POST request.
			// Session needs to be uniquely generated
			int session = 0;
			String requestBody = "{\"session\": \"" + session + "\",\"info\":[";
			requestBody += StringUtils.join(updatesClone, ',');
			requestBody += "]}";

			log.info(requestBody);
			// Construct the request.
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3001/updateMany")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

			// Send out async request.
			httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

			// Clear the updates that have been pushed.
			updatesToPush.removeAll(updatesClone);
		}
	}

	@Provides
	VarbitsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(VarbitsConfig.class);
	}
}
