/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.data.index.elasticsearch.type;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.UserGroup;
import org.kitodo.data.database.beans.UserProperty;

/**
 * Implementation of User Type.
 */
public class UserType /*extends BaseType*/ {

    @SuppressWarnings("unchecked")
    public HttpEntity createDocument(User user) {

        LinkedHashMap<String, String> orderedUserMap = new LinkedHashMap<>();
        orderedUserMap.put("name", user.getName());
        orderedUserMap.put("surname", user.getSurname());
        orderedUserMap.put("login", user.getLogin());
        orderedUserMap.put("ldapLogin", user.getLdapLogin());
        orderedUserMap.put("active", String.valueOf(user.isActive()));
        orderedUserMap.put("location", user.getLocation());
        orderedUserMap.put("metadataLanguage", user.getMetadataLanguage());
        if (user.getLdapGroup() != null) {
            orderedUserMap.put("ldapGroup", user.getLdapGroup().getId().toString());
        }

        JSONObject userObject = new JSONObject(orderedUserMap);

        JSONArray userGroups = new JSONArray();
        List<UserGroup> userUserGroups = user.getUserGroups();
        for (UserGroup userGroup : userUserGroups) {
            JSONObject userGroupObject = new JSONObject();
            userGroupObject.put("id", userGroup.getId().toString());
            userGroups.add(userGroupObject);
        }
        userObject.put("userGroups", userGroups);

        JSONArray properties = new JSONArray();
        List<UserProperty> userProperties = user.getProperties();
        for (UserProperty property : userProperties) {
            JSONObject propertyObject = new JSONObject();
            propertyObject.put("title", property.getTitle());
            propertyObject.put("value", property.getValue());
            properties.add(propertyObject);
        }
        userObject.put("properties", properties);

        return new NStringEntity(userObject.toJSONString(), ContentType.APPLICATION_JSON);
    }

    public HashMap<Integer, HttpEntity> createDocuments(List<User> users) {
        HashMap<Integer, HttpEntity> documents = new HashMap<>();
        for (User user : users) {
            documents.put(user.getId(), createDocument(user));
        }
        return documents;
    }
}
