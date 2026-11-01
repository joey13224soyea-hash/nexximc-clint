package com.nexxi.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * access.json looks like:
 * {
 *   "denyMessage": "Sorry, you have not been able to join.",
 *   "users":  { "Nexxi": "owner", "Steve": "member" },
 *   "banned": [ "SomeGriefer" ]
 * }
 */
public class AccessJson {
    public static void load(AccessList list, String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        list.users.clear();
        list.banned.clear();
        list.denyMessage = o.has("denyMessage") ? o.get("denyMessage").getAsString() : AccessList.DEFAULT_DENY;
        if (o.has("users")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("users").entrySet()) {
                list.users.put(e.getKey(), e.getValue().getAsString());
            }
        }
        if (o.has("banned")) {
            for (JsonElement el : o.getAsJsonArray("banned")) {
                list.banned.add(el.getAsString());
            }
        }
    }

    public static String toJson(AccessList list) {
        JsonObject o = new JsonObject();
        o.addProperty("denyMessage", list.denyMessage);
        JsonObject users = new JsonObject();
        list.users.forEach(users::addProperty);
        o.add("users", users);
        JsonArray banned = new JsonArray();
        list.banned.forEach(banned::add);
        o.add("banned", banned);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o);
    }
}
