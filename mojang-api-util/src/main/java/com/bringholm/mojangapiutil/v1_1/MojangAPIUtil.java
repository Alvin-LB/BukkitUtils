package com.bringholm.mojangapiutil.v1_1;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation to make requests to Mojang's API servers.
 * See http://wiki.vg/Mojang_API for more information.
 * <p>
 * Since all of these methods require connections to Mojang's servers, all of them
 * execute asynchronously, and do therefor not return any values. Instead, a callback mechanism
 * is implemented, which allows for processing of data returned from these requests.
 * If an error occurs when retrieving the data, the 'successful' boolean in the callback
 * will be set to false. In these cases, null will be passed to the callback, even if
 * some data has been received.
 * <p>
 * Each method has an synchronous and an asynchronous version. It is recommended that you
 * use the synchronous version unless you're intending to do more tasks that should be
 * executed asynchronously.
 *
 * @author AlvinB
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class MojangAPIUtil {
    private static URL API_STATUS_URL = null;
    private static URL GET_UUID_URL = null;
    private static final JSONParser PARSER = new JSONParser();

    private static Plugin plugin;

    static {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getClass().getProtectionDomain().getCodeSource().equals(MojangAPIUtil.class.getProtectionDomain().getCodeSource())) {
                MojangAPIUtil.plugin = plugin;
            }
        }
        try {
            API_STATUS_URL = new URL("https://status.mojang.com/check");
            GET_UUID_URL = new URL("https://api.mojang.com/profiles/minecraft");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the plugin instance to use for scheduler tasks.
     * <p>
     * The plugin instance in the same jar as this class should automatically be found, so only
     * use this if you for whatever reason need to use another plugin instance.
     *
     * @param plugin the plugin instance
     */
    public void setPlugin(Plugin plugin) {
        MojangAPIUtil.plugin = plugin;
    }

    /**
     * Same as #getAPIStatusAsync, but the callback is executed synchronously
     */
    public static void getAPIStatusWithCallBack(ResultCallBack<Map<String, APIStatus>> callBack) {
        getAPIStatusAsyncWithCallBack((successful, result, exception) -> new BukkitRunnable() {
            @Override
            public void run() {
                callBack.callBack(successful, result, exception);
            }
        }.runTask(plugin));
    }

    /**
     * Gets the current state of Mojang's API
     * <p>
     * The keys of the map passed to the callback is the service, and the value is the current state of the service.
     * Statuses can be either RED (meaning service unavailable), YELLOW (meaning service available,
     * but with some issues) and GREEN (meaning service fully functional).
     *
     * @param callBack the callback of the request
     * @see APIStatus
     */
    @SuppressWarnings("unchecked")
    public static void getAPIStatusAsyncWithCallBack(ResultCallBack<Map<String, APIStatus>> callBack) {
        if (plugin == null) {
            return;
        }
        makeAsyncGetRequest(API_STATUS_URL, (successful, response, exception, responseCode) -> {
            if (callBack == null) {
                return;
            }
            if (successful && responseCode == 200) {
                try {
                    Map<String, APIStatus> map = Maps.newHashMap();
                    JSONArray jsonArray = (JSONArray) PARSER.parse(response);
                    for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
                        for (JSONObject.Entry<String, String> entry : ((Map<String, String>) jsonObject).entrySet()) {
                            map.put(entry.getKey(), APIStatus.fromString(entry.getValue()));
                        }
                    }
                    callBack.callBack(true, map, null);
                } catch (Exception e) {
                    callBack.callBack(false, null, e);
                }
            } else {
                if (exception != null) {
                    callBack.callBack(false, null, exception);
                } else {
                    callBack.callBack(false, null, new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
                }
            }
        });
    }

    /**
     * The statuses of Mojang's API used by getAPIStatus().
     */
    public enum APIStatus {
        RED,
        YELLOW,
        GREEN;

        public static APIStatus fromString(String string) {
            switch (string) {
                case "red":
                    return RED;
                case "yellow":
                    return YELLOW;
                case "green":
                    return GREEN;
                default:
                    throw new IllegalArgumentException("Unknown status: " + string);
            }
        }
    }

    /**
     * Same as #getUUIDAtTimeAsync, but the callback is executed synchronously
     */
    public static void getUUIDAtTimeWithCallBack(String username, long timeStamp, ResultCallBack<UUIDAtTime> callBack) {
        getUUIDAtTimeAsyncWithCallBack(username, timeStamp, (successful, result, exception) -> new BukkitRunnable() {
            @Override
            public void run() {
                callBack.callBack(successful, result, exception);
            }
        }.runTask(plugin));
    }

    /**
     * Gets the UUID of a name at a certain point in time
     * <p>
     * The timestamp is in UNIX Time, and if -1 is used as the timestamp,
     * it will get the current user who has this name.
     * <p>
     * The callback contains the UUID and the current username of the UUID.
     * If the username was not occupied at the specified time, the next
     * person to occupy the name will be returned, provided that the name
     * has been changed away from at least once or is legacy. If the name
     * hasn't been changed away from and is not legacy, the value passed
     * to the callback will be null.
     *
     * @param username  the username of the player to do the UUID lookup on
     * @param timeStamp the timestamp when the name was occupied
     * @param callBack  the callback of the request
     */
    public static void getUUIDAtTimeAsyncWithCallBack(String username, long timeStamp, ResultCallBack<UUIDAtTime> callBack) {
        if (plugin == null) {
            return;
        }
        Validate.notNull(username);
        Validate.isTrue(!username.isEmpty(), "username cannot be empty");
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username + (timeStamp != -1 ? "?at=" + timeStamp : ""));
            makeAsyncGetRequest(url, (successful, response, exception, responseCode) -> {
                if (callBack == null) {
                    return;
                }
                if (successful && (responseCode == 200 || responseCode == 204)) {
                    try {
                        UUIDAtTime[] uuidAtTime = new UUIDAtTime[1];
                        if (responseCode == 200) {
                            JSONObject object = (JSONObject) PARSER.parse(response);
                            String uuidString = (String) object.get("id");
                            uuidAtTime[0] = new UUIDAtTime((String) object.get("name"), getUUIDFromString(uuidString));
                        }
                        callBack.callBack(true, uuidAtTime[0], null);
                    } catch (Exception e) {
                        callBack.callBack(false, null, e);
                    }
                } else {
                    if (exception != null) {
                        callBack.callBack(false, null, exception);
                    } else {
                        callBack.callBack(false, null, new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
                    }
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public static class UUIDAtTime {
        private String name;
        private UUID uuid;

        public UUIDAtTime(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public UUID getUUID() {
            return uuid;
        }

        @Override
        public String toString() {
            return "UUIDAtTime{name=" + name + ",uuid=" + uuid + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof UUIDAtTime)) {
                return false;
            }
            UUIDAtTime uuidAtTime = (UUIDAtTime) obj;
            return this.name.equals(uuidAtTime.name) && this.uuid.equals(uuidAtTime.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.name, this.uuid);
        }
    }

    /**
     * Same as #getNameHistoryAsync, but the callback is executed synchronously
     */
    public static void getNameHistoryWithCallBack(UUID uuid, ResultCallBack<Map<String, Long>> callBack) {
        getNameHistoryAsyncWithCallBack(uuid, (successful, result, exception) -> new BukkitRunnable() {
            @Override
            public void run() {
                callBack.callBack(successful, result, exception);
            }
        }.runTask(plugin));
    }

    /**
     * Gets the name history of a certain UUID
     * <p>
     * The callback is passed a Map<String, Long>, the String being the name,
     * and the long being the UNIX millisecond timestamp the user changed to
     * that name. If the name was the original name of the user, the long will
     * be -1L.
     * <p>
     * If an unused UUID is supplied, an empty Map will be passed to the callback.
     *
     * @param uuid     the uuid of the account
     * @param callBack the callback of the request
     */
    @SuppressWarnings("unchecked")
    public static void getNameHistoryAsyncWithCallBack(UUID uuid, ResultCallBack<Map<String, Long>> callBack) {
        if (plugin == null) {
            return;
        }
        Validate.notNull(uuid, "uuid cannot be null!");
        try {
            URL url = new URL("https://api.mojang.com/user/profiles/" + uuid.toString().replace("-", "") + "/names");
            makeAsyncGetRequest(url, (successful, response, exception, responseCode) -> {
                if (callBack == null) {
                    return;
                }
                if (successful && (responseCode == 200 || responseCode == 204)) {
                    try {
                        Map<String, Long> map = Maps.newHashMap();
                        if (responseCode == 200) {
                            JSONArray jsonArray = (JSONArray) PARSER.parse(response);
                            for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
                                String name = (String) jsonObject.get("name");
                                if (jsonObject.containsKey("changedToAt")) {
                                    map.put(name, (Long) jsonObject.get("changedToAt"));
                                } else {
                                    map.put(name, -1L);
                                }
                            }
                        }
                        callBack.callBack(true, map, null);
                    } catch (Exception e) {
                        callBack.callBack(false, null, e);
                    }
                } else {
                    if (exception != null) {
                        callBack.callBack(false, null, exception);
                    } else {
                        callBack.callBack(false, null, new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
                    }
                }
            });
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public static void getUUIDWithCallBack(ResultCallBack<Map<String, Profile>> callBack, String... usernames) {
        getUUIDWithCallBack(Arrays.asList(usernames), callBack);
    }

    /**
     * Same as #getUUIDAsync, but the callback is executed synchronously
     */
    public static void getUUIDWithCallBack(List<String> usernames, ResultCallBack<Map<String, Profile>> callBack) {
        getUUIDAsyncWithCallBack(usernames, (successful, result, exception) -> new BukkitRunnable() {
            @Override
            public void run() {
                callBack.callBack(successful, result, exception);
            }
        }.runTask(plugin));
    }

    public static void getUUIDAsyncWithCallBack(ResultCallBack<Map<String, Profile>> callBack, String... usernames) {
        getUUIDAsyncWithCallBack(Arrays.asList(usernames), callBack);
    }

    /**
     * Same as #getUUIDWithCallBack but is entirely executed
     * on the current thread. Should be used with caution to avoid
     * blocking any important activities on the current thread.
     */
    @SuppressWarnings("unchecked")
    public static Result<Map<String, Profile>> getUUID(List<String> usernames) {
        if (plugin == null) {
            return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
        }
        Validate.notNull(usernames, "usernames cannot be null");
        Validate.isTrue(usernames.size() <= 100, "cannot request more than 100 usernames at once");
        JSONArray usernameJson = new JSONArray();
        usernameJson.addAll(usernames.stream().filter(s -> !Strings.isNullOrEmpty(s)).collect(Collectors.toList()));
        RequestResult result = makeSyncPostRequest(GET_UUID_URL, usernameJson.toJSONString());
        if (result == null) {
            return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
        }
        try {
            if (result.successful && result.responseCode == 200) {
                Map<String, Profile> map = Maps.newHashMap();
                JSONArray jsonArray = (JSONArray) PARSER.parse(result.response);
                //noinspection Duplicates
                for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
                    String uuidString = (String) jsonObject.get("id");
                    String name = (String) jsonObject.get("name");
                    boolean legacy = false;
                    if (jsonObject.containsKey("legacy")) {
                        legacy = (boolean) jsonObject.get("legacy");
                    }
                    boolean unpaid = false;
                    if (jsonObject.containsKey("demo")) {
                        unpaid = (boolean) jsonObject.get("demo");
                    }
                    map.put(name, new Profile(getUUIDFromString(uuidString), name, legacy, unpaid));
                }
                return new Result<>(map, true, null);
            } else {
                if (result.exception != null) {
                    return new Result<>(null, false, result.exception);
                } else {
                    return new Result<>(null, false, new IOException("Failed to obtain Mojang data! Response code: " + result.responseCode));
                }
            }
        } catch (Exception e) {
            return new Result<>(null, false, e);
        }
    }

    /**
     * Gets the Profiles of up to 100 usernames.
     *
     * @param usernames the usernames
     * @param callBack  the callback
     */
    @SuppressWarnings("unchecked")
    public static void getUUIDAsyncWithCallBack(List<String> usernames, ResultCallBack<Map<String, Profile>> callBack) {
        if (plugin == null) {
            return;
        }
        Validate.notNull(usernames, "usernames cannot be null");
        Validate.isTrue(usernames.size() <= 100, "cannot request more than 100 usernames at once");
        JSONArray usernameJson = new JSONArray();
        usernameJson.addAll(usernames.stream().filter(s -> !Strings.isNullOrEmpty(s)).collect(Collectors.toList()));
        makeAsyncPostRequest(GET_UUID_URL, usernameJson.toJSONString(), (successful, response, exception, responseCode) -> {
            if (callBack == null) {
                return;
            }
            try {
                if (successful && responseCode == 200) {
                    Map<String, Profile> map = Maps.newHashMap();
                    JSONArray jsonArray = (JSONArray) PARSER.parse(response);
                    //noinspection Duplicates
                    for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
                        String uuidString = (String) jsonObject.get("id");
                        String name = (String) jsonObject.get("name");
                        boolean legacy = false;
                        if (jsonObject.containsKey("legacy")) {
                            legacy = (boolean) jsonObject.get("legacy");
                        }
                        boolean unpaid = false;
                        if (jsonObject.containsKey("demo")) {
                            unpaid = (boolean) jsonObject.get("demo");
                        }
                        map.put(name, new Profile(getUUIDFromString(uuidString), name, legacy, unpaid));
                    }
                    callBack.callBack(true, map, null);
                } else {
                    if (exception != null) {
                        callBack.callBack(false, null, exception);
                    } else {
                        callBack.callBack(false, null, new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
                    }
                }
            } catch (Exception e) {
                callBack.callBack(false, null, e);
            }
        });
    }

    public static class Profile {
        private UUID uuid;
        private String name;
        private boolean legacy;
        private boolean unpaid;

        Profile(UUID uuid, String name, boolean legacy, boolean unpaid) {
            this.uuid = uuid;
            this.name = name;
            this.legacy = legacy;
            this.unpaid = unpaid;
        }

        public UUID getUUID() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public boolean isLegacy() {
            return legacy;
        }

        public boolean isUnpaid() {
            return unpaid;
        }

        @Override
        public String toString() {
            return "Profile{uuid=" + uuid + ", name=" + name + ", legacy=" + legacy + ", unpaid=" + unpaid + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Profile)) {
                return false;
            }
            Profile otherProfile = (Profile) obj;
            return uuid.equals(otherProfile.uuid) && name.equals(otherProfile.name) && legacy == otherProfile.legacy && unpaid == otherProfile.unpaid;
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, name, legacy, unpaid);
        }
    }

    /**
     * Same as #getSkinDataWithCallBack but is entirely executed
     * on the current thread. Should be used with caution to avoid
     * blocking any important activities on the current thread.
     */
    @SuppressWarnings("unchecked")
    public static Result<SkinData> getSkinData(UUID uuid) {
        if (plugin == null) {
            return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
        }
        URL url;
        try {
            url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
        } catch (MalformedURLException e) {
            return new Result<>(null, false, e);
        }
        RequestResult result = makeSyncGetRequest(url);
        if (result == null) {
            return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
        }
        try {
            if (result.successful && (result.responseCode == 200 || result.responseCode == 204)) {
                if (result.responseCode == 204) {
                    return new Result<>(null, true, null);
                }
                JSONObject object = (JSONObject) PARSER.parse(result.response);
                JSONArray propertiesArray = (JSONArray) object.get("properties");
                String base64 = null;
                for (JSONObject property : (List<JSONObject>) propertiesArray) {
                    String name = (String) property.get("name");
                    if (name.equals("textures")) {
                        base64 = (String) property.get("value");
                    }
                }
                if (base64 == null) {
                    return new Result<>(null, true, null);
                }
                String decodedBase64 = new String(Base64.getDecoder().decode(base64), "UTF-8");
                JSONObject base64json = (JSONObject) PARSER.parse(decodedBase64);
                long timeStamp = (long) base64json.get("timestamp");
                String profileName = (String) base64json.get("profileName");
                UUID profileId = getUUIDFromString((String) base64json.get("profileId"));
                JSONObject textures = (JSONObject) base64json.get("textures");
                String skinURL = null;
                String capeURL = null;
                if (textures.containsKey("SKIN")) {
                    JSONObject skinObject = (JSONObject) textures.get("SKIN");
                    skinURL = (String) skinObject.get("url");
                }
                if (textures.containsKey("CAPE")) {
                    JSONObject capeObject = (JSONObject) textures.get("CAPE");
                    capeURL = (String) capeObject.get("url");
                }
                return new Result<>(new SkinData(profileId, profileName, skinURL, capeURL, timeStamp, base64), true, null);
            } else {
                if (result.exception != null) {
                    return new Result<>(null, false, result.exception);
                } else {
                    return new Result<>(null, false, new IOException("Failed to obtain Mojang data! Response code: " + result.responseCode));
                }
            }
        } catch (Exception e) {
            return new Result<>(null, false, e);
        }
    }

    /**
     * Same as #getSkinDataAsync, but the callback is executed synchronously
     */
    public static void getSkinData(UUID uuid, ResultCallBack<SkinData> callBack) {
        getSkinDataAsync(uuid, (successful, result, exception) -> new BukkitRunnable() {
            @Override
            public void run() {
                callBack.callBack(successful, result, exception);
            }
        }.runTask(plugin));
    }

    /**
     * Gets the Skin data for a certain user. If the user cannot
     * be found, the value passed to the callback will be null.
     *
     * @param uuid the uuid of the user
     * @param callBack the callback
     */
    @SuppressWarnings("unchecked")
    public static void getSkinDataAsync(UUID uuid, ResultCallBack<SkinData> callBack) {
        if (plugin == null) {
            return;
        }
        URL url;
        try {
            url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return;
        }
        makeAsyncGetRequest(url, (successful, response, exception, responseCode) -> {
            try {
                if (successful && (responseCode == 200 || responseCode == 204)) {
                    if (responseCode == 204) {
                        callBack.callBack(true, null, null);
                        return;
                    }
                    JSONObject object = (JSONObject) PARSER.parse(response);
                    JSONArray propertiesArray = (JSONArray) object.get("properties");
                    String base64 = null;
                    for (JSONObject property : (List<JSONObject>) propertiesArray) {
                        String name = (String) property.get("name");
                        if (name.equals("textures")) {
                            base64 = (String) property.get("value");
                        }
                    }
                    if (base64 == null) {
                        callBack.callBack(true, null, null);
                        return;
                    }
                    String decodedBase64 = new String(Base64.getDecoder().decode(base64), "UTF-8");
                    JSONObject base64json = (JSONObject) PARSER.parse(decodedBase64);
                    long timeStamp = (long) base64json.get("timestamp");
                    String profileName = (String) base64json.get("profileName");
                    UUID profileId = getUUIDFromString((String) base64json.get("profileId"));
                    JSONObject textures = (JSONObject) base64json.get("textures");
                    String skinURL = null;
                    String capeURL = null;
                    if (textures.containsKey("SKIN")) {
                        JSONObject skinObject = (JSONObject) textures.get("SKIN");
                        skinURL = (String) skinObject.get("url");
                    }
                    if (textures.containsKey("CAPE")) {
                        JSONObject capeObject = (JSONObject) textures.get("CAPE");
                        capeURL = (String) capeObject.get("url");
                    }
                    callBack.callBack(true, new SkinData(profileId, profileName, skinURL, capeURL, timeStamp, base64), null);
                } else {
                    if (exception != null) {
                        callBack.callBack(false, null, exception);
                    } else {
                        callBack.callBack(false, null, new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
                    }
                }
            } catch (Exception e) {
                callBack.callBack(false, null, e);
            }
        });
    }

    public static class SkinData {
        private UUID uuid;
        private String name;
        private String skinURL;
        private String capeURL;
        private long timeStamp;
        private String base64;

        public SkinData(UUID uuid, String name, String skinURL, String capeURL, long timeStamp, String base64) {
            this.uuid = uuid;
            this.name = name;
            this.skinURL = skinURL;
            this.capeURL = capeURL;
            this.timeStamp = timeStamp;
            this.base64 = base64;
        }

        public UUID getUUID() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public boolean hasSkinURL() {
            return skinURL != null;
        }

        public String getSkinURL() {
            return skinURL;
        }

        public boolean hasCapeURL() {
            return capeURL != null;
        }

        public String getCapeURL() {
            return capeURL;
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public String getBase64() {
            return base64;
        }

        @Override
        public String toString() {
            return "SkinData{uuid=" + uuid + ",name=" + name + ",skinURL=" + skinURL + ",capeURL=" + capeURL + ",timeStamp=" + timeStamp + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof SkinData)) {
                return false;
            }
            SkinData skinData = (SkinData) obj;
            return this.uuid.equals(skinData.uuid) && this.name.equals(skinData.name) &&
                    (this.skinURL == null ? skinData.skinURL == null : this.skinURL.equals(skinData.skinURL)) &&
                    (this.capeURL == null ? skinData.capeURL == null : this.capeURL.equals(skinData.skinURL)) && this.timeStamp == skinData.timeStamp;

        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, name, skinURL, capeURL, timeStamp);
        }
    }

    private static RequestResult makeSyncGetRequest(URL url) {
        if (plugin == null) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            //noinspection Duplicates
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line = reader.readLine();
                while (line != null) {
                    response.append(line);
                    line = reader.readLine();
                }
                RequestResult result = new RequestResult();
                result.successful = true;
                result.responseCode = connection.getResponseCode();
                result.response = response.toString();
                return result;
            }
        } catch (IOException e) {
            RequestResult result = new RequestResult();
            result.exception = e;
            result.successful = false;
            return result;
        }
    }

    private static void makeAsyncGetRequest(URL url, RequestCallBack asyncCallBack) {
        if (plugin == null) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                StringBuilder response = new StringBuilder();
                try {
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();
                    //noinspection Duplicates
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line = reader.readLine();
                        while (line != null) {
                            response.append(line);
                            line = reader.readLine();
                        }
                        asyncCallBack.callBack(true, response.toString(), null, connection.getResponseCode());
                    }
                } catch (Exception e) {
                    asyncCallBack.callBack(false, response.toString(), e, -1);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private static RequestResult makeSyncPostRequest(URL url, String payload) {
        if (plugin == null) {
            return null;
        }
        StringBuilder response = new StringBuilder();
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.connect();
            try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
                writer.write(payload);
            }
            //noinspection Duplicates
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line = reader.readLine();
                while (line != null) {
                    response.append(line);
                    line = reader.readLine();
                }
                RequestResult result = new RequestResult();
                result.successful = true;
                result.responseCode = connection.getResponseCode();
                result.response = response.toString();
                return result;
            }
        } catch (IOException e) {
            RequestResult result = new RequestResult();
            result.successful = false;
            result.exception = e;
            return result;
        }
    }

    private static void makeAsyncPostRequest(URL url, String payload, RequestCallBack asyncCallBack) {
        if (plugin == null) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                StringBuilder response = new StringBuilder();
                try {
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.connect();
                    try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
                        writer.write(payload);
                    }
                    //noinspection Duplicates
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line = reader.readLine();
                        while (line != null) {
                            response.append(line);
                            line = reader.readLine();
                        }
                        asyncCallBack.callBack(true, response.toString(), null, connection.getResponseCode());
                    }
                } catch (Exception e) {
                    asyncCallBack.callBack(false, response.toString(), e, -1);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public static UUID getUUIDFromString(String string) {
        String uuidString = string.substring(0, 8) + "-" + string.substring(8, 12) + "-" + string.substring(12, 16) + "-" + string.substring(16, 20) + "-" +
                string.substring(20);
        return UUID.fromString(uuidString);
    }

    @FunctionalInterface
    private interface RequestCallBack {
        void callBack(boolean successful, String response, Exception exception, int responseCode);
    }

    private static class RequestResult {
        boolean successful;
        String response;
        Exception exception;
        int responseCode;
    }

    /**
     * The callback interface
     * <p>
     * Once some data is received (or an error is thrown)
     * the callBack method is fired with the following data:
     * <p>
     * boolean successful - If the data arrived and was interpreted correctly.
     * <p>
     * <T> result - The data. Only present if successful is true, otherwise null.
     * <p>
     * Exception e - The exception. Only present if successful is false, otherwise null.
     * <p>
     * This interface is annotated with @FunctionalInterface, which allows for instantiation
     * using lambda expressions.
     */
    @FunctionalInterface
    public interface ResultCallBack<T> {
        void callBack(boolean successful, T result, Exception exception);
    }

    public static class Result<T> {
        private T value;
        private boolean successful;
        private Exception exception;

        public Result(T value, boolean successful, Exception exception) {
            this.value = value;
            this.successful = successful;
            this.exception = exception;
        }

        public T getValue() {
            return value;
        }

        public boolean wasSuccessful() {
            return successful;
        }

        public Exception getException() {
            return exception;
        }
    }
}
