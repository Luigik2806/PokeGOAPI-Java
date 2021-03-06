/*
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.pokegoapi.api;

import POGOProtos.Enums.PlatformOuterClass;
import POGOProtos.Enums.PlatformOuterClass.Platform;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo;
import POGOProtos.Networking.Envelopes.SignatureOuterClass;
import POGOProtos.Networking.Requests.Messages.GetAssetDigestMessageOuterClass.GetAssetDigestMessage;
import POGOProtos.Networking.Requests.RequestTypeOuterClass;
import POGOProtos.Networking.Requests.RequestTypeOuterClass.RequestType;
import POGOProtos.Networking.Requests.Messages.CheckAwardedBadgesMessageOuterClass.CheckAwardedBadgesMessage;
import POGOProtos.Networking.Requests.Messages.DownloadRemoteConfigVersionMessageOuterClass.DownloadRemoteConfigVersionMessage;
import POGOProtos.Networking.Requests.Messages.DownloadSettingsMessageOuterClass.DownloadSettingsMessage;
import POGOProtos.Networking.Requests.Messages.GetHatchedEggsMessageOuterClass.GetHatchedEggsMessage;
import POGOProtos.Networking.Requests.Messages.GetInventoryMessageOuterClass.GetInventoryMessage;
import POGOProtos.Networking.Responses.DownloadSettingsResponseOuterClass.DownloadSettingsResponse;
import POGOProtos.Networking.Responses.GetInventoryResponseOuterClass.GetInventoryResponse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pokegoapi.api.device.ActivityStatus;
import com.pokegoapi.api.device.DeviceInfo;
import com.pokegoapi.api.device.LocationFixes;
import com.pokegoapi.api.device.SensorInfo;
import com.pokegoapi.api.inventory.Inventories;
import com.pokegoapi.api.map.Map;
import com.pokegoapi.api.player.PlayerProfile;
import com.pokegoapi.api.settings.Settings;
import com.pokegoapi.auth.CredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.main.RequestHandler;
import com.pokegoapi.main.ServerRequest;
import com.pokegoapi.util.Constant;
import com.pokegoapi.util.SystemTimeImpl;
import com.pokegoapi.util.Time;

import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;

import java.util.Random;
import java.util.UUID;


public class PokemonGo {

	private static final java.lang.String TAG = PokemonGo.class.getSimpleName();
	private final Time time;
	@Getter
	private long startTime;
	@Getter
	private final byte[] sessionHash;
	@Getter
	RequestHandler requestHandler;
	@Getter
	private PlayerProfile playerProfile;
	private Inventories inventories;
	@Getter
	private double latitude;
	@Getter
	private double longitude;
	@Getter
	@Setter
	private double altitude;
	private CredentialProvider credentialProvider;
	@Getter
	private Settings settings;
	private Map map;
	@Setter
	private DeviceInfo deviceInfo;
	@Getter
	@Setter
	public SensorInfo sensorInfo;
	@Getter
	@Setter
	public ActivityStatus activityStatus;
	@Setter
	@Getter
	private long seed;
	@Getter
	@Setter
	public LocationFixes locationFixes;

	/**
	 * Instantiates a new Pokemon go.
	 *
	 * @param client the http client
	 * @param time   a time implementation
	 * @param seed   the seed to generate same device
	 */
	public PokemonGo(OkHttpClient client, Time time, long seed) {
		this.time = time;
		this.seed = seed;
		sessionHash = new byte[32];
		new Random().nextBytes(sessionHash);
		requestHandler = new RequestHandler(this, client);
		map = new Map(this);
		longitude = Double.NaN;
		latitude = Double.NaN;
	}

	/**
	 * Instantiates a new Pokemon go.
	 * Deprecated: specify a time implementation
	 *
	 * @param client the http client
	 * @param seed   the seed to generate same device
	 */
	public PokemonGo(OkHttpClient client, long seed) {
		this(client, new SystemTimeImpl(), seed);
	}

	/**
	 * Instantiates a new Pokemon go.
	 * Deprecated: specify a time implementation
	 *
	 * @param client the http client
	 * @param time   a time implementation
	 */
	public PokemonGo(OkHttpClient client, Time time) {
		this(client, time, hash(UUID.randomUUID().toString()));
	}

	/**
	 * Instantiates a new Pokemon go.
	 * Deprecated: specify a time implementation
	 *
	 * @param client the http client
	 */
	public PokemonGo(OkHttpClient client) {
		this(client, new SystemTimeImpl(), hash(UUID.randomUUID().toString()));
	}

	/**
	 * Login user with the provided provider
	 *
	 * @param credentialProvider the credential provider
	 * @throws LoginFailedException  When login fails
	 * @throws RemoteServerException When server fails
	 */
	public void login(CredentialProvider credentialProvider) throws LoginFailedException, RemoteServerException {
		if (credentialProvider == null) {
			throw new NullPointerException("Credential Provider is null");
		}
		this.credentialProvider = credentialProvider;
		startTime = currentTimeMillis();
		playerProfile = new PlayerProfile(this);
		settings = new Settings(this);
		inventories = new Inventories(this);

		initialize();
	}

	private void initialize() throws RemoteServerException, LoginFailedException {
		ServerRequest[] requests = new ServerRequest[5];
		final DownloadRemoteConfigVersionMessage downloadRemoteConfigReq = DownloadRemoteConfigVersionMessage
				.newBuilder()
				.setPlatform(Platform.IOS)
				.setAppVersion(Constant.APP_VERSION)
				.build();

		requests[0] = new ServerRequest(RequestType.DOWNLOAD_REMOTE_CONFIG_VERSION, downloadRemoteConfigReq);
		requests[1] = new ServerRequest(RequestType.GET_HATCHED_EGGS, GetHatchedEggsMessage.getDefaultInstance());
		requests[2] = new ServerRequest(RequestType.GET_INVENTORY, GetInventoryMessage.getDefaultInstance());
		requests[3] = new ServerRequest(RequestType.CHECK_AWARDED_BADGES, CheckAwardedBadgesMessage.getDefaultInstance());
		requests[4] = new ServerRequest(RequestType.DOWNLOAD_SETTINGS, DownloadSettingsMessage.getDefaultInstance());
		getRequestHandler().sendServerRequests(requests);
		try {
			inventories.updateInventories(GetInventoryResponse.parseFrom(requests[2].getData()));
			settings.updateSettings(DownloadSettingsResponse.parseFrom(requests[4].getData()));
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException();
		}


		final GetAssetDigestMessage getAssetDigestReq = GetAssetDigestMessage.newBuilder()
				.setPlatform(PlatformOuterClass.Platform.IOS)
				.setAppVersion(Constant.APP_VERSION)
				.build();
		final GetInventoryMessage getInventoryReq = GetInventoryMessage.newBuilder()
				.setLastTimestampMs(inventories.getLastInventoryUpdate())
				.build();
		final DownloadSettingsMessage downloadSettingsReq = DownloadSettingsMessage
				.newBuilder().setHash(settings.getHash()).build();

		requests[0] = new ServerRequest(RequestTypeOuterClass.RequestType.GET_ASSET_DIGEST,
				getAssetDigestReq);
		requests[1] = new ServerRequest(RequestTypeOuterClass.RequestType.GET_HATCHED_EGGS,
				GetHatchedEggsMessage.getDefaultInstance());
		requests[2] = new ServerRequest(RequestTypeOuterClass.RequestType.GET_INVENTORY,
				getInventoryReq);
		requests[3] = new ServerRequest(RequestTypeOuterClass.RequestType.CHECK_AWARDED_BADGES,
				CheckAwardedBadgesMessage.getDefaultInstance());
		requests[4] = new ServerRequest(RequestType.DOWNLOAD_SETTINGS, downloadSettingsReq);
		getRequestHandler().sendServerRequests(requests);
		try {
			inventories.updateInventories(GetInventoryResponse.parseFrom(requests[2].getData()));
			settings.updateSettings(DownloadSettingsResponse.parseFrom(requests[4].getData()));
		} catch (InvalidProtocolBufferException e) {
			throw new RemoteServerException();
		}
	}

	/**
	 * Hash the given string
	 *
	 * @param string string to hash
	 * @return the hashed long
	 */
	private static long hash(String string) {
		long upper = ((long) string.hashCode()) << 32;
		int len = string.length();
		StringBuilder dest = new StringBuilder(len);

		for (int index = (len - 1); index >= 0; index--) {
			dest.append(string.charAt(index));
		}
		long lower = ((long) dest.toString().hashCode()) - ((long) Integer.MIN_VALUE);
		return upper + lower;
	}

	/**
	 * Fetches valid AuthInfo
	 *
	 * @return AuthInfo object
	 * @throws LoginFailedException  when login fails
	 * @throws RemoteServerException When server fails
	 */
	public AuthInfo getAuthInfo()
			throws LoginFailedException, RemoteServerException {
		return credentialProvider.getAuthInfo();
	}

	/**
	 * Sets location.
	 *
	 * @param latitude  the latitude
	 * @param longitude the longitude
	 * @param altitude  the altitude
	 */
	public void setLocation(double latitude, double longitude, double altitude) {
		if (latitude != this.latitude
				|| longitude != this.longitude
				|| altitude != this.altitude) {
			getMap().clearCache();
		}
		setLatitude(latitude);
		setLongitude(longitude);
		setAltitude(altitude);
	}

	public long currentTimeMillis() {
		return time.currentTimeMillis();
	}

	/**
	 * Get the inventories API
	 *
	 * @return Inventories
	 */
	public Inventories getInventories() {
		return inventories;
	}

	/**
	 * Validates and sets a given latitude value
	 *
	 * @param value the latitude
	 * @throws IllegalArgumentException if value exceeds +-90
	 */
	public void setLatitude(double value) {
		if (value > 90 || value < -90) {
			throw new IllegalArgumentException("latittude can not exceed +/- 90");
		}
		latitude = value;
	}

	/**
	 * Validates and sets a given longitude value
	 *
	 * @param value the longitude
	 * @throws IllegalArgumentException if value exceeds +-180
	 */
	public void setLongitude(double value) {
		if (value > 180 || value < -180) {
			throw new IllegalArgumentException("longitude can not exceed +/- 180");
		}
		longitude = value;
	}

	/**
	 * Gets the map API
	 *
	 * @return the map
	 * @throws IllegalStateException if location has not been set
	 */
	public Map getMap() {
		if (this.latitude == Double.NaN || this.longitude == Double.NaN) {
			throw new IllegalStateException("Attempt to get map without setting location first");
		}
		return map;
	}

	/**
	 * Gets the device info
	 *
	 * @return the device info
	 */
	public SignatureOuterClass.Signature.DeviceInfo getDeviceInfo() {
		if (deviceInfo == null) {
			deviceInfo = DeviceInfo.getDefault(this);
		}
		return deviceInfo.getDeviceInfo();
	}
}
