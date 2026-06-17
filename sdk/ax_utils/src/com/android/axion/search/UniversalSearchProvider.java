/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.search;

import android.Manifest;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.LruCache;
import android.util.Size;

import com.android.axion.util.PackageManagerUtils;
import com.android.axion.utils.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class UniversalSearchProvider {
    private static final int MAX_APP_RESULTS = 10;
    private static final int MAX_IN_APP_SEARCH_ACTIONS = 3;
    private static final int MAX_IMAGE_RESULTS = 3;
    private static final int MAX_FILE_RESULTS = 3;
    private static final int MAX_SETTINGS_RESULTS = 3;
    private static final int MAX_CONTACT_RESULTS = 5;
    private static final int MAX_CALENDAR_RESULTS = 3;
    private static final int MAX_WEB_RESULTS = 5;
    private static final int MAX_MEDIA_RESULTS_PER_SOURCE = 3;
    private static final int MAX_RICH_MEDIA_RESULTS = 3;
    private static final int IMAGE_CACHE_SIZE = 24;
    private static final int RICH_RESULT_CACHE_SIZE = 32;
    private static final int NETWORK_TIMEOUT_MILLIS = 1500;
    private static final int MAX_RESPONSE_CHARS = 5_000_000;
    private static final String GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final String SPOTIFY_PACKAGE = "com.spotify.music";
    private static final String YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music";
    private static final String GOOGLE_MAPS_HOST = "www.google.com/maps";
    private static final String YOUTUBE_HOST = "www.youtube.com";
    private static final String SPOTIFY_HOST = "open.spotify.com";
    private static final String YOUTUBE_MUSIC_HOST = "music.youtube.com";
    private static final LruCache<String, Bitmap> sImageCache = new LruCache<>(IMAGE_CACHE_SIZE);
    private static final LruCache<String, ArrayList<RichResult>> sRichResultCache =
            new LruCache<>(RICH_RESULT_CACHE_SIZE);

    private UniversalSearchProvider() { }

    public static ArrayList<UniversalSearchResult> getSearchResults(Context context, String query) {
        return getSearchResults(context, query, true);
    }

    public static ArrayList<UniversalSearchResult> getSearchResults(Context context, String query,
            boolean includeApps) {
        return getSearchResults(context, query, includeApps, UniversalSearchResult.TYPE_MASK_ALL);
    }

    public static ArrayList<UniversalSearchResult> getSearchResults(Context context, String query,
            boolean includeApps, int enabledTypes) {
        String queryText = query == null ? "" : query.trim();
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        if (queryText.isEmpty()) {
            return result;
        }
        String queryTextLower = queryText.toLowerCase();
        String normalizedQuery = normalize(queryText);
        if (includeApps && isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_APP)) {
            result.addAll(getInstalledAppResults(context, queryText, queryTextLower,
                    normalizedQuery));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_ANSWER)) {
            result.addAll(getCalculatorResults(context, queryText));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_SETTINGS)) {
            result.addAll(getSettingsSearchResults(context, queryText, queryTextLower,
                    normalizedQuery));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_CONTACT)) {
            result.addAll(getContactSearchResults(context, queryText));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_IMAGE)) {
            result.addAll(getImageSearchResults(context, queryText));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_FILE)) {
            result.addAll(getFileSearchResults(context, queryText));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_CALENDAR)) {
            result.addAll(getCalendarSearchResults(context, queryText));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_WEB)
                || isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_MEDIA)) {
            result.addAll(getWebSearchActions(context, queryText, enabledTypes));
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_IN_APP)) {
            result.addAll(getInAppSearchActions(context, queryText, queryTextLower,
                    normalizedQuery));
        }
        return result;
    }

    private static ArrayList<UniversalSearchResult> getInstalledAppResults(Context context,
            String queryText, String queryTextLower, String normalizedQuery) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0);
        ArrayList<ScoredResult> scoredResults = new ArrayList<>();
        Set<ComponentName> seenComponents = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            ComponentName componentName = new ComponentName(activityInfo.packageName,
                    activityInfo.name);
            if (!seenComponents.add(componentName)) {
                continue;
            }
            CharSequence label = loadLabel(resolveInfo, packageManager, activityInfo.name);
            String title = label.toString();
            int score = getTextMatchScore(queryText, queryTextLower, normalizedQuery, title);
            if (score <= 0 && containsLower(activityInfo.packageName, queryTextLower)) {
                score = 35;
            }
            if (score <= 0) {
                continue;
            }
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(componentName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            UniversalSearchResult result = new UniversalSearchResult(UniversalSearchResult.TYPE_APP,
                    title, activityInfo.packageName, loadIcon(resolveInfo, packageManager), intent,
                    false, false, false);
            scoredResults.add(new ScoredResult(result, score, title));
        }
        scoredResults.sort(UniversalSearchProvider::compareScoredResults);
        return collectTopResults(scoredResults, MAX_APP_RESULTS);
    }

    private static ArrayList<UniversalSearchResult> getInAppSearchActions(Context context,
            String queryText, String queryTextLower, String normalizedQuery) {
        SearchManager searchManager = context.getSystemService(SearchManager.class);
        if (searchManager == null) {
            return new ArrayList<>();
        }
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_SEARCH), 0);
        ArrayList<ScoredResult> scoredResults = new ArrayList<>();
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null || !seenPackages.add(activityInfo.packageName)
                    || isInternalSearchPackage(context, activityInfo.packageName)
                    || !PackageManagerUtils.isPackageLaunchable(context, activityInfo.packageName)) {
                continue;
            }
            ComponentName componentName = new ComponentName(activityInfo.packageName,
                    activityInfo.name);
            if (searchManager.getSearchableInfo(componentName) == null) {
                continue;
            }
            CharSequence label = loadLabel(resolveInfo, packageManager, activityInfo.packageName);
            String title = label.toString();
            int score = getTextMatchScore(queryText, queryTextLower, normalizedQuery, title);
            if (score <= 0) {
                continue;
            }
            Intent intent = new Intent(Intent.ACTION_SEARCH)
                    .setComponent(componentName)
                    .putExtra(SearchManager.QUERY, queryText)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            UniversalSearchResult result = new UniversalSearchResult(
                    UniversalSearchResult.TYPE_IN_APP,
                    context.getString(R.string.ax_search_action_app_title, title), queryText,
                    loadIcon(resolveInfo, packageManager), intent, false, false, false);
            scoredResults.add(new ScoredResult(result, score, title));
        }
        scoredResults.sort(UniversalSearchProvider::compareScoredResults);
        return collectTopResults(scoredResults, MAX_IN_APP_SEARCH_ACTIONS);
    }

    private static ArrayList<UniversalSearchResult> getCalculatorResults(Context context,
            String queryText) {
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        Calculation calculation = evaluateCalculation(queryText);
        if (calculation == null) {
            return result;
        }
        Intent intent = getCalculatorIntent(context, queryText);
        if (intent == null) {
            return result;
        }
        result.add(new UniversalSearchResult(UniversalSearchResult.TYPE_ANSWER,
                calculation.result, calculation.expression,
                getDrawable(context, R.drawable.ax_ic_search_calculator),
                intent, true, false, false));
        return result;
    }

    private static ArrayList<UniversalSearchResult> getSettingsSearchResults(Context context,
            String queryText, String queryTextLower, String normalizedQuery) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).setPackage(SETTINGS_PACKAGE),
                PackageManager.MATCH_ALL);
        ArrayList<ScoredResult> scoredResults = new ArrayList<>();
        Drawable settingsIcon = PackageManagerUtils.getApplicationIcon(context, SETTINGS_PACKAGE);
        for (ResolveInfo resolveInfo : resolveInfos) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            CharSequence label = loadLabel(resolveInfo, packageManager, activityInfo.name);
            String title = label.toString();
            int score = getTextMatchScore(queryText, queryTextLower, normalizedQuery, title);
            if (score <= 0) {
                continue;
            }
            Intent intent = new Intent()
                    .setClassName(activityInfo.packageName, activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable icon = settingsIcon == null ? getDrawable(context, R.drawable.ax_ic_search_settings)
                    : settingsIcon;
            scoredResults.add(new ScoredResult(new UniversalSearchResult(
                    UniversalSearchResult.TYPE_SETTINGS, title,
                    context.getString(R.string.ax_search_action_settings_subtitle), icon, intent,
                    settingsIcon == null, false, false), score, title));
        }
        scoredResults.sort(UniversalSearchProvider::compareScoredResults);
        return collectTopResults(scoredResults, MAX_SETTINGS_RESULTS);
    }

    private static ArrayList<UniversalSearchResult> getContactSearchResults(Context context,
            String queryText) {
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        if (!hasContactSearchPermission(context)) {
            return result;
        }
        String[] projection = new String[] {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        };
        String selection = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?";
        String sortOrder = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                + " COLLATE LOCALIZED ASC";
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI, projection, selection,
                new String[] {likeArg(queryText)}, sortOrder)) {
            if (cursor == null) {
                return result;
            }
            int idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID);
            int lookupIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY);
            int nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY);
            int photoIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI);
            boolean addedPhoneActions = false;
            while (cursor.moveToNext() && result.size() < MAX_CONTACT_RESULTS) {
                String name = cursor.getString(nameIndex);
                String lookupKey = cursor.getString(lookupIndex);
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(lookupKey)) {
                    continue;
                }
                Uri lookupUri = ContactsContract.Contacts.getLookupUri(
                        cursor.getLong(idIndex), lookupKey);
                if (lookupUri == null) {
                    continue;
                }
                String photoUri = cursor.getString(photoIndex);
                Drawable photo = TextUtils.isEmpty(photoUri) ? null
                        : getDrawableFromUri(context, Uri.parse(photoUri));
                Intent intent = new Intent(Intent.ACTION_VIEW, lookupUri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                result.add(new UniversalSearchResult(UniversalSearchResult.TYPE_CONTACT, name,
                        context.getString(R.string.ax_search_action_contact_subtitle),
                        photo == null ? getDrawable(context, R.drawable.ax_ic_search_contact)
                                : photo,
                        intent, photo == null, photo != null, true));
                if (!addedPhoneActions) {
                    String phoneNumber = getPrimaryPhoneNumber(context, cursor.getLong(idIndex));
                    if (!TextUtils.isEmpty(phoneNumber)) {
                        addContactPhoneAction(context, result,
                                R.string.ax_search_action_call_contact_title, name, phoneNumber,
                                new Intent(Intent.ACTION_DIAL,
                                        Uri.fromParts("tel", phoneNumber, null)));
                        addContactPhoneAction(context, result,
                                R.string.ax_search_action_message_contact_title, name, phoneNumber,
                                new Intent(Intent.ACTION_SENDTO,
                                        Uri.fromParts("smsto", phoneNumber, null)));
                        addedPhoneActions = true;
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return result;
        }
        return result;
    }

    private static void addContactPhoneAction(Context context,
            ArrayList<UniversalSearchResult> result, int titleRes, String name,
            String phoneNumber, Intent intent) {
        if (result.size() >= MAX_CONTACT_RESULTS) {
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!PackageManagerUtils.isActivityResolvable(context, intent)) {
            return;
        }
        result.add(new UniversalSearchResult(UniversalSearchResult.TYPE_CONTACT,
                context.getString(titleRes, name), phoneNumber,
                getDrawable(context, R.drawable.ax_ic_search_contact), intent, true, false, true));
    }

    private static String getPrimaryPhoneNumber(Context context, long contactId) {
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
        };
        String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?";
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection,
                new String[] {String.valueOf(contactId)}, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            return cursor.getString(cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER));
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private static ArrayList<UniversalSearchResult> getImageSearchResults(Context context,
            String queryText) {
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        if (!hasImageSearchPermission(context)) {
            return result;
        }
        String[] projection = new String[] {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
        };
        String selection = MediaStore.Images.Media.DISPLAY_NAME + " LIKE ?";
        try (Cursor cursor = queryMediaStore(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, new String[] {likeArg(queryText)}, MAX_IMAGE_RESULTS)) {
            if (cursor == null) {
                return result;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
            while (cursor.moveToNext() && result.size() < MAX_IMAGE_RESULTS) {
                long id = cursor.getLong(idIndex);
                String name = cursor.getString(nameIndex);
                if (TextUtils.isEmpty(name)) {
                    continue;
                }
                String mimeType = cursor.getString(mimeIndex);
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                Intent intent = getContentIntent(uri, TextUtils.isEmpty(mimeType) ? "image/*"
                        : mimeType);
                Drawable thumbnail = getThumbnail(context, uri);
                result.add(new UniversalSearchResult(UniversalSearchResult.TYPE_IMAGE, name,
                        context.getString(R.string.ax_search_action_image_subtitle),
                        thumbnail == null ? getDrawable(context, R.drawable.ax_ic_search_image)
                                : thumbnail,
                        intent, thumbnail == null, thumbnail != null, true));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return result;
        }
        return result;
    }

    private static ArrayList<UniversalSearchResult> getFileSearchResults(Context context,
            String queryText) {
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        if (!hasFileSearchPermission(context)) {
            return result;
        }
        Uri collectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String[] projection = new String[] {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
        };
        String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.Files.FileColumns.MIME_TYPE + " IS NOT NULL AND "
                + "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + " IS NULL OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + "!="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + ")";
        try (Cursor cursor = queryMediaStore(context, collectionUri, projection, selection,
                new String[] {likeArg(queryText)}, MAX_FILE_RESULTS)) {
            if (cursor == null) {
                return result;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            while (cursor.moveToNext() && result.size() < MAX_FILE_RESULTS) {
                long id = cursor.getLong(idIndex);
                String name = cursor.getString(nameIndex);
                String mimeType = cursor.getString(mimeIndex);
                long size = cursor.getLong(sizeIndex);
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(mimeType)) {
                    continue;
                }
                Uri uri = ContentUris.withAppendedId(collectionUri, id);
                result.add(new UniversalSearchResult(UniversalSearchResult.TYPE_FILE, name,
                        Formatter.formatFileSize(context, size), getFileIcon(context, mimeType),
                        getContentIntent(uri, mimeType), true, false, true));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return result;
        }
        return result;
    }

    private static ArrayList<UniversalSearchResult> getCalendarSearchResults(Context context,
            String queryText) {
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        if (!hasCalendarSearchPermission(context)) {
            return result;
        }
        String[] projection = new String[] {
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
        };
        String selection = CalendarContract.Events.TITLE + " LIKE ? AND "
                + CalendarContract.Events.DTSTART + ">=?";
        String[] selectionArgs = new String[] {
                likeArg(queryText),
                String.valueOf(System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS),
        };
        String sortOrder = CalendarContract.Events.DTSTART + " ASC";
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs,
                sortOrder)) {
            if (cursor == null) {
                return result;
            }
            int idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID);
            int titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE);
            int locationIndex = cursor.getColumnIndexOrThrow(
                    CalendarContract.Events.EVENT_LOCATION);
            int startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART);
            int endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND);
            while (cursor.moveToNext() && result.size() < MAX_CALENDAR_RESULTS) {
                String title = cursor.getString(titleIndex);
                if (TextUtils.isEmpty(title)) {
                    continue;
                }
                long id = cursor.getLong(idIndex);
                long start = cursor.getLong(startIndex);
                long end = cursor.getLong(endIndex);
                String location = cursor.getString(locationIndex);
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (!PackageManagerUtils.isActivityResolvable(context, intent)) {
                    continue;
                }
                result.add(new UniversalSearchResult(UniversalSearchResult.TYPE_CALENDAR, title,
                        getCalendarSubtitle(context, start, location),
                        getDrawable(context, R.drawable.ax_ic_search_calendar), intent,
                        true, false, true));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return result;
        }
        return result;
    }

    private static String getCalendarSubtitle(Context context, long start, String location) {
        String when = DateUtils.formatDateTime(context, start,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_ABBREV_MONTH);
        return joinNonEmpty(when, location, "");
    }

    private static Cursor queryMediaStore(Context context, Uri collectionUri, String[] projection,
            String selection, String[] selectionArgs, int limit) {
        Bundle queryArgs = new Bundle();
        queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs);
        queryArgs.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,
                new String[] {MediaStore.MediaColumns.DATE_MODIFIED});
        queryArgs.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, limit);
        return context.getContentResolver().query(collectionUri, projection, queryArgs, null);
    }

    private static Intent getContentIntent(Uri uri, String mimeType) {
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private static ArrayList<UniversalSearchResult> getWebSearchActions(Context context,
            String queryText, int enabledTypes) {
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_WEB)) {
            int startSize = result.size();
            addWebSuggestionResults(context, result, queryText);
            addMapResult(context, result, queryText);
            if (result.size() == startSize) {
                addWebAction(context, result,
                        context.getString(R.string.ax_search_action_google_query_title, queryText),
                        queryText, GOOGLE_SEARCH_PACKAGE, R.drawable.ax_ic_search,
                        getGoogleSearchIntent(context, queryText));
                addWebAction(context, result,
                        context.getString(R.string.ax_search_action_web_query_title, queryText),
                        queryText, null, R.drawable.ax_ic_search_language,
                        getWebSearchIntent(context, queryText));
                addWebAction(context, result,
                        context.getString(R.string.ax_search_action_store_query_title, queryText),
                        queryText, PLAY_STORE_PACKAGE, R.drawable.ax_ic_search,
                        getStoreSearchIntent(context, queryText));
            }
        }
        if (isTypeEnabled(enabledTypes, UniversalSearchResult.TYPE_MEDIA)) {
            String youtubeMusicQuery = getKeywordQuery(queryText, "youtube music", "yt music",
                    "music youtube");
            String youtubeQuery = youtubeMusicQuery == null
                    ? getKeywordQuery(queryText, "youtube", "yt") : null;
            String spotifyQuery = getKeywordQuery(queryText, "spotify");
            String youtubeQueryText = youtubeQuery == null ? queryText : youtubeQuery;
            String spotifyQueryText = spotifyQuery == null ? queryText : spotifyQuery;
            String youtubeMusicQueryText = youtubeMusicQuery == null ? queryText
                    : youtubeMusicQuery;
            if (youtubeMusicQuery == null) {
                addMediaResults(context, result,
                        context.getString(R.string.ax_search_action_youtube_title),
                        youtubeQueryText, YOUTUBE_PACKAGE, R.drawable.ax_ic_search_language,
                        getAppSearchIntent(context, YOUTUBE_PACKAGE, youtubeQueryText,
                                youtubeQuery != null), youtubeQuery != null);
            }
            addMediaResults(context, result,
                    context.getString(R.string.ax_search_action_spotify_title),
                    spotifyQueryText, SPOTIFY_PACKAGE, R.drawable.ax_ic_search_language,
                    getAppSearchIntent(context, SPOTIFY_PACKAGE, spotifyQueryText,
                            spotifyQuery != null), spotifyQuery != null);
            addMediaResults(context, result,
                    context.getString(R.string.ax_search_action_youtube_music_title),
                    youtubeMusicQueryText, YOUTUBE_MUSIC_PACKAGE,
                    R.drawable.ax_ic_search_language,
                    getAppSearchIntent(context, YOUTUBE_MUSIC_PACKAGE, youtubeMusicQueryText,
                            youtubeMusicQuery != null), youtubeMusicQuery != null);
        }
        return result;
    }

    private static void addMapResult(Context context, ArrayList<UniversalSearchResult> result,
            String queryText) {
        String mapQuery = getKeywordQuery(queryText, "maps", "map", "directions", "navigate",
                "navigation");
        if (mapQuery == null) {
            return;
        }
        addSearchAction(context, result, UniversalSearchResult.TYPE_WEB,
                context.getString(R.string.ax_search_action_maps_title), mapQuery,
                GOOGLE_MAPS_PACKAGE, R.drawable.ax_ic_search_language,
                getMapsSearchIntent(context, mapQuery), null, GOOGLE_MAPS_HOST);
    }

    private static void addWebAction(Context context, ArrayList<UniversalSearchResult> result,
            CharSequence title, CharSequence subtitle, String packageName, int fallbackIconRes,
            Intent intent) {
        addSearchAction(context, result, UniversalSearchResult.TYPE_WEB, title, subtitle,
                packageName, fallbackIconRes, intent, null, null);
    }

    private static void addMediaAction(Context context, ArrayList<UniversalSearchResult> result,
            CharSequence title, CharSequence subtitle, String packageName, int fallbackIconRes,
            Intent intent) {
        addSearchAction(context, result, UniversalSearchResult.TYPE_MEDIA, title, subtitle,
                packageName, fallbackIconRes, intent, packageName, getWebsiteHost(packageName));
    }

    private static void addMediaResults(Context context, ArrayList<UniversalSearchResult> result,
            CharSequence fallbackTitle, String queryText, String packageName, int fallbackIconRes,
            Intent fallbackIntent) {
        addMediaResults(context, result, fallbackTitle, queryText, packageName, fallbackIconRes,
                fallbackIntent, false);
    }

    private static void addMediaResults(Context context, ArrayList<UniversalSearchResult> result,
            CharSequence fallbackTitle, String queryText, String packageName, int fallbackIconRes,
            Intent fallbackIntent, boolean allowRichResults) {
        int startSize = result.size();
        addMediaSuggestionResults(context, result, queryText, packageName, fallbackIconRes);
        if (result.size() == startSize && allowRichResults) {
            addRichMediaResults(context, result, queryText, packageName, fallbackIconRes);
        }
        if (result.size() == startSize) {
            addMediaAction(context, result, fallbackTitle, queryText, packageName, fallbackIconRes,
                    fallbackIntent);
        }
    }

    private static void addWebSuggestionResults(Context context,
            ArrayList<UniversalSearchResult> result, String queryText) {
        addKnowledgeResult(context, result, queryText);
        addSearchableSuggestionResults(context, result, queryText, GOOGLE_SEARCH_PACKAGE,
                R.drawable.ax_ic_search, UniversalSearchResult.TYPE_WEB, null, MAX_WEB_RESULTS);
    }

    private static void addMediaSuggestionResults(Context context,
            ArrayList<UniversalSearchResult> result, String queryText, String packageName,
            int fallbackIconRes) {
        addSearchableSuggestionResults(context, result, queryText, packageName, fallbackIconRes,
                UniversalSearchResult.TYPE_MEDIA, packageName, MAX_MEDIA_RESULTS_PER_SOURCE);
    }

    private static void addKnowledgeResult(Context context,
            ArrayList<UniversalSearchResult> result, String queryText) {
        ArrayList<RichResult> richResults = getCachedRichResults("web:" + queryText,
                () -> fetchKnowledgeResults(queryText));
        addRichResults(context, result, richResults, UniversalSearchResult.TYPE_WEB, null,
                R.drawable.ax_ic_search_language);
    }

    private static void addRichMediaResults(Context context,
            ArrayList<UniversalSearchResult> result, String queryText, String packageName,
            int fallbackIconRes) {
        ArrayList<RichResult> richResults;
        if (YOUTUBE_PACKAGE.equals(packageName)) {
            richResults = getCachedRichResults("youtube:" + queryText,
                    () -> fetchYouTubeResults(queryText));
        } else if (SPOTIFY_PACKAGE.equals(packageName)) {
            richResults = getCachedRichResults("spotify:" + queryText,
                    () -> fetchSpotifyResults(queryText));
        } else {
            return;
        }
        int startSize = result.size();
        addRichResults(context, result, richResults, UniversalSearchResult.TYPE_MEDIA,
                packageName, fallbackIconRes);
        if (YOUTUBE_PACKAGE.equals(packageName) && result.size() > startSize) {
            addMediaAction(context, result,
                    context.getString(R.string.ax_search_action_show_more_results), queryText,
                    packageName, fallbackIconRes,
                    getAppSearchIntent(context, packageName, queryText, true));
        }
    }

    private static void addRichResults(Context context,
            ArrayList<UniversalSearchResult> result, ArrayList<RichResult> richResults, int type,
            String sourcePackage, int fallbackIconRes) {
        int total = richResults.size();
        for (int i = 0; i < total; i++) {
            RichResult richResult = richResults.get(i);
            Intent intent = getViewIntent(context, richResult.url);
            if (intent == null) {
                intent = getViewIntent(context, richResult.fallbackUrl);
            }
            if (intent == null) {
                continue;
            }
            Drawable image = TextUtils.isEmpty(richResult.imageUrl) ? null
                    : getRemoteImage(context, richResult.imageUrl, "image:" + richResult.imageUrl);
            result.add(new UniversalSearchResult(type, richResult.title, richResult.subtitle,
                    image == null ? getDrawable(context, fallbackIconRes) : image, intent,
                    image == null, image != null, true, sourcePackage));
        }
    }

    private static ArrayList<RichResult> getCachedRichResults(String key,
            RichResultFetcher fetcher) {
        ArrayList<RichResult> cached = sRichResultCache.get(key);
        if (cached != null) {
            return cached;
        }
        ArrayList<RichResult> results = fetcher.fetch();
        sRichResultCache.put(key, results);
        return results;
    }

    private static ArrayList<RichResult> fetchKnowledgeResults(String queryText) {
        ArrayList<RichResult> result = new ArrayList<>();
        if (queryText.length() < 3 || Looper.myLooper() == Looper.getMainLooper()) {
            return result;
        }
        Uri uri = new Uri.Builder()
                .scheme("https")
                .authority("api.duckduckgo.com")
                .appendPath("")
                .appendQueryParameter("q", queryText)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("no_html", "1")
                .appendQueryParameter("skip_disambig", "1")
                .build();
        try {
            JSONObject object = new JSONObject(readUrl(uri));
            String title = object.optString("Heading");
            String subtitle = object.optString("AbstractText");
            String imageUrl = normalizeDuckDuckGoImageUrl(object.optString("Image"));
            String url = object.optString("AbstractURL");
            if (TextUtils.isEmpty(url)) {
                url = getGoogleSearchUrl(queryText);
            }
            if (!TextUtils.isEmpty(title) && (!TextUtils.isEmpty(subtitle)
                    || !TextUtils.isEmpty(imageUrl))) {
                result.add(new RichResult(title, subtitle, imageUrl, url));
            }
        } catch (JSONException | IOException | RuntimeException e) {
            return result;
        }
        return result;
    }

    private static ArrayList<RichResult> fetchYouTubeResults(String queryText) {
        ArrayList<RichResult> result = new ArrayList<>();
        if (queryText.length() < 2 || Looper.myLooper() == Looper.getMainLooper()) {
            return result;
        }
        Uri uri = new Uri.Builder()
                .scheme("https")
                .authority(YOUTUBE_HOST)
                .path("results")
                .appendQueryParameter("search_query", queryText)
                .build();
        try {
            String response = readUrl(uri);
            String json = extractJsonObject(response, "var ytInitialData = ");
            if (json == null) {
                json = extractJsonObject(response, "ytInitialData = ");
            }
            if (json != null) {
                collectYouTubeResults(new JSONObject(json), result);
            }
        } catch (JSONException | IOException | RuntimeException e) {
            return result;
        }
        return result;
    }

    private static ArrayList<RichResult> fetchSpotifyResults(String queryText) {
        ArrayList<RichResult> result = new ArrayList<>();
        addItunesResults(result, queryText, "music", "musicTrack", 2);
        if (result.size() < MAX_RICH_MEDIA_RESULTS) {
            addItunesResults(result, queryText, "podcast", "podcastEpisode",
                    MAX_RICH_MEDIA_RESULTS - result.size());
        }
        return result;
    }

    private static void addItunesResults(ArrayList<RichResult> result, String queryText,
            String media, String entity, int limit) {
        if (limit <= 0 || Looper.myLooper() == Looper.getMainLooper()) {
            return;
        }
        Uri uri = new Uri.Builder()
                .scheme("https")
                .authority("itunes.apple.com")
                .path("search")
                .appendQueryParameter("term", queryText)
                .appendQueryParameter("media", media)
                .appendQueryParameter("entity", entity)
                .appendQueryParameter("limit", String.valueOf(limit))
                .build();
        try {
            JSONArray results = new JSONObject(readUrl(uri)).optJSONArray("results");
            if (results == null) {
                return;
            }
            for (int i = 0; i < results.length() && result.size() < MAX_RICH_MEDIA_RESULTS; i++) {
                JSONObject object = results.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String title = firstNonEmpty(object.optString("trackName"),
                        object.optString("collectionName"));
                String artist = object.optString("artistName");
                if (TextUtils.isEmpty(title)) {
                    continue;
                }
                String imageUrl = firstNonEmpty(object.optString("artworkUrl100"),
                        object.optString("artworkUrl60"));
                String searchQuery = getSearchQuery(title, artist, queryText);
                String searchUrl = getSpotifySearchUrl(searchQuery);
                result.add(new RichResult(title, artist, imageUrl, searchUrl, searchUrl));
            }
        } catch (JSONException | IOException | RuntimeException e) {
        }
    }

    private static void collectYouTubeResults(Object object, ArrayList<RichResult> result)
            throws JSONException {
        if (result.size() >= MAX_RICH_MEDIA_RESULTS || object == null) {
            return;
        }
        if (object instanceof JSONObject jsonObject) {
            JSONObject renderer = jsonObject.optJSONObject("videoRenderer");
            if (renderer != null) {
                addYouTubeRenderer(renderer, result);
                return;
            }
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext() && result.size() < MAX_RICH_MEDIA_RESULTS) {
                collectYouTubeResults(jsonObject.opt(keys.next()), result);
            }
        } else if (object instanceof JSONArray jsonArray) {
            for (int i = 0; i < jsonArray.length() && result.size() < MAX_RICH_MEDIA_RESULTS; i++) {
                collectYouTubeResults(jsonArray.opt(i), result);
            }
        }
    }

    private static void addYouTubeRenderer(JSONObject renderer, ArrayList<RichResult> result) {
        String videoId = renderer.optString("videoId");
        String title = getYouTubeText(renderer.optJSONObject("title"));
        if (TextUtils.isEmpty(videoId) || TextUtils.isEmpty(title)) {
            return;
        }
        String channel = getYouTubeText(renderer.optJSONObject("shortBylineText"));
        String views = getYouTubeText(renderer.optJSONObject("viewCountText"));
        String date = getYouTubeText(renderer.optJSONObject("publishedTimeText"));
        String subtitle = joinNonEmpty(channel, views, date);
        result.add(new RichResult(title, subtitle, getYouTubeThumbnailUrl(renderer),
                new Uri.Builder()
                        .scheme("https")
                        .authority(YOUTUBE_HOST)
                        .path("watch")
                        .appendQueryParameter("v", videoId)
                        .build()
                        .toString()));
    }

    private static String getYouTubeText(JSONObject object) {
        if (object == null) {
            return "";
        }
        String simpleText = object.optString("simpleText");
        if (!TextUtils.isEmpty(simpleText)) {
            return simpleText;
        }
        JSONArray runs = object.optJSONArray("runs");
        if (runs == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run != null) {
                builder.append(run.optString("text"));
            }
        }
        return builder.toString();
    }

    private static String getYouTubeThumbnailUrl(JSONObject renderer) {
        JSONObject thumbnail = renderer.optJSONObject("thumbnail");
        JSONArray thumbnails = thumbnail == null ? null : thumbnail.optJSONArray("thumbnails");
        if (thumbnails == null || thumbnails.length() == 0) {
            return "";
        }
        JSONObject object = thumbnails.optJSONObject(thumbnails.length() - 1);
        return object == null ? "" : object.optString("url");
    }

    private static void addSearchableSuggestionResults(Context context,
            ArrayList<UniversalSearchResult> result, String queryText, String packageName,
            int fallbackIconRes, int type, String sourcePackage, int limit) {
        SearchableInfo searchableInfo = getSearchableInfo(context, packageName);
        if (searchableInfo == null || TextUtils.isEmpty(searchableInfo.getSuggestAuthority())) {
            return;
        }
        CharSequence appLabel = getApplicationLabel(context, packageName);
        Set<String> seenTitles = new HashSet<>();
        try (Cursor cursor = querySuggestions(context, searchableInfo, queryText, limit)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext() && seenTitles.size() < limit) {
                String title = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_TEXT_1);
                if (TextUtils.isEmpty(title) || !seenTitles.add(title)) {
                    continue;
                }
                String subtitle = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_TEXT_2);
                if (TextUtils.isEmpty(subtitle)) {
                    subtitle = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_TEXT_2_URL);
                }
                Intent intent = createSuggestionIntent(searchableInfo, cursor, queryText);
                if (intent == null || !PackageManagerUtils.isActivityResolvable(context, intent)) {
                    continue;
                }
                Drawable thumbnail = getSuggestionDrawable(context, searchableInfo, cursor,
                        SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE);
                if (thumbnail == null) {
                    thumbnail = getSuggestionDrawable(context, searchableInfo, cursor,
                            SearchManager.SUGGEST_COLUMN_ICON_2);
                }
                Drawable icon = thumbnail == null ? getSuggestionDrawable(context, searchableInfo,
                        cursor, SearchManager.SUGGEST_COLUMN_ICON_1) : thumbnail;
                if (icon == null) {
                    icon = PackageManagerUtils.getApplicationIcon(context, packageName);
                }
                result.add(new UniversalSearchResult(type, title,
                        TextUtils.isEmpty(subtitle) ? appLabel : subtitle,
                        icon == null ? getDrawable(context, fallbackIconRes) : icon, intent,
                        icon == null, thumbnail != null, true, sourcePackage));
            }
        } catch (RuntimeException e) {
            return;
        }
    }

    private static void addSearchAction(Context context, ArrayList<UniversalSearchResult> result,
            int type, CharSequence title, CharSequence subtitle, String packageName,
            int fallbackIconRes, Intent intent, String sourcePackage, String websiteHost) {
        if (intent == null) {
            return;
        }
        Drawable icon = packageName == null ? null
                : PackageManagerUtils.getApplicationIcon(context, packageName);
        if (icon == null && websiteHost != null) {
            icon = getWebsiteIcon(context, websiteHost);
        }
        result.add(new UniversalSearchResult(type, title, subtitle,
                icon == null ? getDrawable(context, fallbackIconRes) : icon, intent,
                icon == null, false, true, sourcePackage));
    }

    private static SearchableInfo getSearchableInfo(Context context, String packageName) {
        SearchManager searchManager = context.getSystemService(SearchManager.class);
        if (searchManager == null || !PackageManagerUtils.isPackageInstalled(context, packageName)) {
            return null;
        }
        SearchableInfo fallback = null;
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_SEARCH).setPackage(packageName), 0);
        for (ResolveInfo resolveInfo : resolveInfos) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            SearchableInfo searchableInfo = searchManager.getSearchableInfo(
                    new ComponentName(activityInfo.packageName, activityInfo.name));
            if (searchableInfo != null) {
                if (!TextUtils.isEmpty(searchableInfo.getSuggestAuthority())) {
                    return searchableInfo;
                }
                fallback = searchableInfo;
            }
        }
        for (SearchableInfo searchableInfo : searchManager.getSearchablesInGlobalSearch()) {
            if (!packageName.equals(searchableInfo.getSearchActivity().getPackageName())) {
                continue;
            }
            if (!TextUtils.isEmpty(searchableInfo.getSuggestAuthority())) {
                return searchableInfo;
            }
            fallback = searchableInfo;
        }
        return fallback;
    }

    private static Cursor querySuggestions(Context context, SearchableInfo searchableInfo,
            String queryText, int limit) {
        Uri.Builder uriBuilder = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(searchableInfo.getSuggestAuthority())
                .query("")
                .fragment("");
        String contentPath = searchableInfo.getSuggestPath();
        if (contentPath != null) {
            uriBuilder.appendEncodedPath(contentPath);
        }
        uriBuilder.appendPath(SearchManager.SUGGEST_URI_PATH_QUERY);
        String selection = searchableInfo.getSuggestSelection();
        String[] selectionArgs = null;
        if (selection == null) {
            uriBuilder.appendPath(queryText);
        } else {
            selectionArgs = new String[] {queryText};
        }
        if (limit > 0) {
            uriBuilder.appendQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT,
                    String.valueOf(limit));
        }
        return context.getContentResolver().query(uriBuilder.build(), null, selection,
                selectionArgs, null);
    }

    private static Intent createSuggestionIntent(SearchableInfo searchableInfo, Cursor cursor,
            String queryText) {
        String action = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_INTENT_ACTION);
        if (action == null) {
            action = searchableInfo.getSuggestIntentAction();
        }
        if (action == null) {
            action = Intent.ACTION_SEARCH;
        }
        String data = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_INTENT_DATA);
        if (data == null) {
            data = searchableInfo.getSuggestIntentData();
        }
        if (data != null) {
            String dataId = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID);
            if (dataId != null) {
                data = data + "/" + Uri.encode(dataId);
            }
        }
        Intent intent = new Intent(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(SearchManager.USER_QUERY, queryText);
        if (data != null) {
            intent.setData(Uri.parse(data));
        }
        String suggestionQuery = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_QUERY);
        if (!TextUtils.isEmpty(suggestionQuery)) {
            intent.putExtra(SearchManager.QUERY, suggestionQuery);
        } else {
            intent.putExtra(SearchManager.QUERY, queryText);
        }
        String extraData = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA);
        if (extraData != null) {
            intent.putExtra(SearchManager.EXTRA_DATA_KEY, extraData);
        }
        intent.setComponent(searchableInfo.getSearchActivity());
        return intent;
    }

    private static Drawable getSuggestionDrawable(Context context, SearchableInfo searchableInfo,
            Cursor cursor, String columnName) {
        String value = getColumnString(cursor, columnName);
        if (TextUtils.isEmpty(value) || "0".equals(value)) {
            return null;
        }
        try {
            int resId = Integer.parseInt(value);
            String packageName = searchableInfo.getSuggestPackage();
            if (TextUtils.isEmpty(packageName)) {
                packageName = searchableInfo.getSearchActivity().getPackageName();
            }
            return context.createPackageContext(packageName, 0).getDrawable(resId);
        } catch (NumberFormatException e) {
            return getDrawableFromUri(context, Uri.parse(value));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    private static Drawable getDrawableFromUri(Context context, Uri uri) {
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            return stream == null ? null : Drawable.createFromStream(stream, null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static CharSequence getApplicationLabel(Context context, String packageName) {
        ApplicationInfo appInfo = PackageManagerUtils.getApplicationInfo(context, packageName);
        return appInfo == null ? packageName
                : PackageManagerUtils.loadApplicationLabel(context.getPackageManager(), appInfo);
    }

    private static Intent getGoogleSearchIntent(Context context, String queryText) {
        Intent googleIntent = new Intent(Intent.ACTION_WEB_SEARCH)
                .setPackage(GOOGLE_SEARCH_PACKAGE)
                .putExtra(SearchManager.QUERY, queryText)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PackageManagerUtils.isActivityResolvable(context, googleIntent)
                ? googleIntent : null;
    }

    private static Intent getWebSearchIntent(Context context, String queryText) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, new Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .path("search")
                .appendQueryParameter("q", queryText)
                .build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PackageManagerUtils.isActivityResolvable(context, browserIntent)
                ? browserIntent : null;
    }

    private static Intent getStoreSearchIntent(Context context, String queryText) {
        Uri storeUri = new Uri.Builder()
                .scheme("market")
                .authority("search")
                .appendQueryParameter("q", queryText)
                .appendQueryParameter("c", "apps")
                .build();
        Intent playStoreIntent = new Intent(Intent.ACTION_VIEW, storeUri)
                .setPackage(PLAY_STORE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (PackageManagerUtils.isActivityResolvable(context, playStoreIntent)) {
            return playStoreIntent;
        }
        Intent storeIntent = new Intent(Intent.ACTION_VIEW, storeUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PackageManagerUtils.isActivityResolvable(context, storeIntent) ? storeIntent : null;
    }

    private static Intent getMapsSearchIntent(Context context, String queryText) {
        Uri mapsUri = Uri.parse("geo:0,0?q=" + Uri.encode(queryText));
        Intent mapsIntent = new Intent(Intent.ACTION_VIEW, mapsUri)
                .setPackage(GOOGLE_MAPS_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (PackageManagerUtils.isActivityResolvable(context, mapsIntent)) {
            return mapsIntent;
        }
        Intent webIntent = new Intent(Intent.ACTION_VIEW, new Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .appendPath("maps")
                .appendPath("search")
                .appendQueryParameter("api", "1")
                .appendQueryParameter("query", queryText)
                .build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PackageManagerUtils.isActivityResolvable(context, webIntent) ? webIntent : null;
    }

    private static Intent getCalculatorIntent(Context context, String queryText) {
        Intent calculatorIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALCULATOR)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (PackageManagerUtils.isActivityResolvable(context, calculatorIntent)) {
            return calculatorIntent;
        }
        return getWebSearchIntent(context, queryText);
    }

    private static Intent getViewIntent(Context context, String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if ("spotify".equals(uri.getScheme())) {
            intent.setPackage(SPOTIFY_PACKAGE);
        }
        return PackageManagerUtils.isActivityResolvable(context, intent) ? intent : null;
    }

    private static String getGoogleSearchUrl(String queryText) {
        return new Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .path("search")
                .appendQueryParameter("q", queryText)
                .build()
                .toString();
    }

    private static String getSpotifySearchUrl(String queryText) {
        return new Uri.Builder()
                .scheme("https")
                .authority(SPOTIFY_HOST)
                .appendPath("search")
                .appendPath("results")
                .appendPath(queryText)
                .build()
                .toString();
    }

    private static String getSearchQuery(String title, String artist, String fallback) {
        if (TextUtils.isEmpty(title)) {
            return fallback;
        }
        return TextUtils.isEmpty(artist) ? title : title + " " + artist;
    }

    private static Intent getAppSearchIntent(Context context, String packageName, String queryText) {
        return getAppSearchIntent(context, packageName, queryText, false);
    }

    private static Intent getAppSearchIntent(Context context, String packageName, String queryText,
            boolean allowWebFallback) {
        boolean installed = PackageManagerUtils.isPackageInstalled(context, packageName);
        if (!installed && !allowWebFallback) {
            return null;
        }
        Intent intent = buildMediaSearchIntent(packageName, queryText, installed);
        if (intent != null && PackageManagerUtils.isActivityResolvable(context, intent)) {
            return intent;
        }
        if (!allowWebFallback || !installed) {
            return null;
        }
        Intent webIntent = buildMediaSearchIntent(packageName, queryText, false);
        return webIntent != null && PackageManagerUtils.isActivityResolvable(context, webIntent)
                ? webIntent : null;
    }

    private static Intent buildMediaSearchIntent(String packageName, String queryText,
            boolean preferApp) {
        Uri uri;
        if (YOUTUBE_PACKAGE.equals(packageName)) {
            uri = new Uri.Builder()
                    .scheme("https")
                    .authority(YOUTUBE_HOST)
                    .path("results")
                    .appendQueryParameter("search_query", queryText)
                    .build();
        } else if (SPOTIFY_PACKAGE.equals(packageName)) {
            uri = preferApp ? Uri.parse("spotify:search:" + Uri.encode(queryText))
                    : Uri.parse(getSpotifySearchUrl(queryText));
        } else if (YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
            uri = new Uri.Builder()
                    .scheme("https")
                    .authority(YOUTUBE_MUSIC_HOST)
                    .path("search")
                    .appendQueryParameter("q", queryText)
                    .build();
        } else {
            return null;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (preferApp && !YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
            intent.setPackage(packageName);
        }
        return intent;
    }

    private static String getKeywordQuery(String queryText, String... keywords) {
        String lowerQuery = queryText.toLowerCase();
        for (String keyword : keywords) {
            if (lowerQuery.equals(keyword)) {
                return queryText;
            }
            if (lowerQuery.startsWith(keyword + " ")) {
                String strippedQuery = queryText.substring(keyword.length()).trim();
                return strippedQuery.isEmpty() ? queryText : strippedQuery;
            }
            if (lowerQuery.endsWith(" " + keyword)) {
                String strippedQuery = queryText.substring(
                        0, queryText.length() - keyword.length()).trim();
                return strippedQuery.isEmpty() ? queryText : strippedQuery;
            }
        }
        return null;
    }

    private static String getWebsiteHost(String packageName) {
        if (YOUTUBE_PACKAGE.equals(packageName)) {
            return YOUTUBE_HOST;
        }
        if (SPOTIFY_PACKAGE.equals(packageName)) {
            return SPOTIFY_HOST;
        }
        if (YOUTUBE_MUSIC_PACKAGE.equals(packageName)) {
            return YOUTUBE_MUSIC_HOST;
        }
        return null;
    }

    private static Drawable getWebsiteIcon(Context context, String host) {
        Uri iconUri = new Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .appendPath("s2")
                .appendPath("favicons")
                .appendQueryParameter("sz", "128")
                .appendQueryParameter("domain_url", "https://" + host)
                .build();
        return getRemoteImage(context, iconUri.toString(), "favicon:" + host);
    }

    private static Drawable getRemoteImage(Context context, String url, String cacheKey) {
        Bitmap cachedIcon = sImageCache.get(cacheKey);
        if (cachedIcon != null) {
            return new BitmapDrawable(context.getResources(), cachedIcon);
        }
        if (TextUtils.isEmpty(url) || Looper.myLooper() == Looper.getMainLooper()) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = openConnection(Uri.parse(url));
            try (InputStream stream = connection.getInputStream()) {
                Bitmap icon = BitmapFactory.decodeStream(stream);
                if (icon == null) {
                    return null;
                }
                sImageCache.put(cacheKey, icon);
                return new BitmapDrawable(context.getResources(), icon);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readUrl(Uri uri) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(uri);
            try (InputStream stream = connection.getInputStream();
                    InputStreamReader streamReader = new InputStreamReader(stream,
                            StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(streamReader)) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1
                        && builder.length() < MAX_RESPONSE_CHARS) {
                    builder.append(buffer, 0, count);
                }
                return builder.toString();
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection openConnection(Uri uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString())
                .openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        return connection;
    }

    private static String extractJsonObject(String text, String marker) {
        if (text == null) {
            return null;
        }
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int start = text.indexOf('{', markerIndex + marker.length());
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (ch == '\\') {
                    escaping = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static Calculation evaluateCalculation(String queryText) {
        String expression = normalizeCalculationExpression(queryText);
        if (TextUtils.isEmpty(expression) || !hasCalculationOperator(expression)) {
            return null;
        }
        try {
            double value = new CalculatorParser(expression).parse();
            if (!Double.isFinite(value)) {
                return null;
            }
            return new Calculation(expression, formatCalculation(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeCalculationExpression(String queryText) {
        String expression = queryText.replace('×', '*').replace('÷', '/').replace('−', '-');
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (!Character.isDigit(ch) && ch != '.' && ch != '+' && ch != '-' && ch != '*'
                    && ch != '/' && ch != '(' && ch != ')' && ch != '%' && !Character.isSpaceChar(ch)) {
                return "";
            }
        }
        return expression.trim();
    }

    private static boolean hasCalculationOperator(String expression) {
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (ch == '+' || ch == '*' || ch == '/' || ch == '%' || ch == '(' || ch == ')') {
                return true;
            }
            if (ch == '-' && i > 0) {
                return true;
            }
        }
        return false;
    }

    private static String formatCalculation(double value) {
        DecimalFormat format = new DecimalFormat("0.##########",
                DecimalFormatSymbols.getInstance(Locale.getDefault()));
        return format.format(value);
    }

    private static String normalizeDuckDuckGoImageUrl(String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            return "";
        }
        if (imageUrl.startsWith("//")) {
            return "https:" + imageUrl;
        }
        if (imageUrl.startsWith("/")) {
            return "https://duckduckgo.com" + imageUrl;
        }
        return imageUrl;
    }

    private static String firstNonEmpty(String first, String second) {
        return TextUtils.isEmpty(first) ? second : first;
    }

    private static String joinNonEmpty(String first, String second, String third) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, first);
        appendPart(builder, second);
        appendPart(builder, third);
        return builder.toString();
    }

    private static void appendPart(StringBuilder builder, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" • ");
        }
        builder.append(text);
    }

    private static boolean isInternalSearchPackage(Context context, String packageName) {
        return context.getPackageName().equals(packageName)
                || GOOGLE_SEARCH_PACKAGE.equals(packageName)
                || PLAY_STORE_PACKAGE.equals(packageName);
    }

    private static Drawable getThumbnail(Context context, Uri uri) {
        try {
            Bitmap bitmap = context.getContentResolver().loadThumbnail(uri, new Size(256, 256),
                    null);
            return new BitmapDrawable(context.getResources(), bitmap);
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Drawable getFileIcon(Context context, String mimeType) {
        int iconRes = mimeType != null && mimeType.startsWith("image/")
                ? R.drawable.ax_ic_search_image
                : R.drawable.ax_ic_search_file;
        return getDrawable(context, iconRes);
    }

    private static Drawable getDrawable(Context context, int resId) {
        return context.getDrawable(resId);
    }

    private static boolean hasImageSearchPermission(Context context) {
        return Environment.isExternalStorageManager()
                || hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                || hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                || hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private static boolean hasFileSearchPermission(Context context) {
        return Environment.isExternalStorageManager()
                || hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                || hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                || hasPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                || hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                || hasPermission(context, Manifest.permission.READ_MEDIA_AUDIO);
    }

    private static boolean hasContactSearchPermission(Context context) {
        return hasPermission(context, Manifest.permission.READ_CONTACTS);
    }

    private static boolean hasCalendarSearchPermission(Context context) {
        return hasPermission(context, Manifest.permission.READ_CALENDAR);
    }

    private static boolean hasPermission(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isTypeEnabled(int enabledTypes, int type) {
        return (enabledTypes & UniversalSearchResult.getTypeMask(type)) != 0;
    }

    private static String likeArg(String queryText) {
        return "%" + queryText + "%";
    }

    private static CharSequence loadLabel(ResolveInfo resolveInfo, PackageManager packageManager,
            String fallback) {
        try {
            CharSequence label = resolveInfo.loadLabel(packageManager);
            return TextUtils.isEmpty(label) ? fallback : label;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Drawable loadIcon(ResolveInfo resolveInfo, PackageManager packageManager) {
        try {
            return resolveInfo.loadIcon(packageManager);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String getColumnString(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        return index < 0 ? null : cursor.getString(index);
    }

    private static boolean containsLower(String value, String queryTextLower) {
        return value != null && value.toLowerCase().contains(queryTextLower);
    }

    private static int getTextMatchScore(String queryText, String queryTextLower,
            String normalizedQuery, String title) {
        String titleLower = title.toLowerCase();
        String normalizedTitle = normalize(title);
        if (title.equalsIgnoreCase(queryText)) {
            return 100;
        }
        if (!normalizedQuery.isEmpty() && normalizedTitle.equals(normalizedQuery)) {
            return 95;
        }
        if (titleLower.startsWith(queryTextLower)) {
            return 80;
        }
        if (!normalizedQuery.isEmpty() && normalizedTitle.startsWith(normalizedQuery)) {
            return 75;
        }
        if (titleLower.contains(queryTextLower)) {
            return 45;
        }
        return !normalizedQuery.isEmpty() && normalizedTitle.contains(normalizedQuery) ? 40 : 0;
    }

    private static int compareScoredResults(ScoredResult left, ScoredResult right) {
        int scoreComparison = Integer.compare(right.score, left.score);
        return scoreComparison != 0 ? scoreComparison : left.title.compareToIgnoreCase(right.title);
    }

    private static ArrayList<UniversalSearchResult> collectTopResults(
            ArrayList<ScoredResult> scoredResults, int limit) {
        ArrayList<UniversalSearchResult> result = new ArrayList<>();
        int count = Math.min(scoredResults.size(), limit);
        for (int i = 0; i < count; i++) {
            result.add(scoredResults.get(i).result);
        }
        return result;
    }

    private static String normalize(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private interface RichResultFetcher {
        ArrayList<RichResult> fetch();
    }

    private static final class Calculation {
        final String expression;
        final String result;

        Calculation(String expression, String result) {
            this.expression = expression;
            this.result = result;
        }
    }

    private static final class CalculatorParser {
        private final String mExpression;
        private int mIndex;

        CalculatorParser(String expression) {
            mExpression = expression;
        }

        double parse() {
            double value = parseExpression();
            skipSpaces();
            if (mIndex != mExpression.length()) {
                throw new IllegalArgumentException();
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    value /= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipSpaces();
            double value;
            if (match('+')) {
                value = parseFactor();
            } else if (match('-')) {
                value = -parseFactor();
            } else if (match('(')) {
                value = parseExpression();
                if (!match(')')) {
                    throw new IllegalArgumentException();
                }
            } else {
                value = parseNumber();
            }
            skipSpaces();
            while (match('%')) {
                value /= 100d;
                skipSpaces();
            }
            return value;
        }

        private double parseNumber() {
            int start = mIndex;
            boolean hasDot = false;
            while (mIndex < mExpression.length()) {
                char ch = mExpression.charAt(mIndex);
                if (Character.isDigit(ch)) {
                    mIndex++;
                } else if (ch == '.' && !hasDot) {
                    hasDot = true;
                    mIndex++;
                } else {
                    break;
                }
            }
            if (start == mIndex) {
                throw new IllegalArgumentException();
            }
            return Double.parseDouble(mExpression.substring(start, mIndex));
        }

        private boolean match(char expected) {
            skipSpaces();
            if (mIndex < mExpression.length() && mExpression.charAt(mIndex) == expected) {
                mIndex++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (mIndex < mExpression.length()
                    && Character.isSpaceChar(mExpression.charAt(mIndex))) {
                mIndex++;
            }
        }
    }

    private static final class RichResult {
        final String title;
        final String subtitle;
        final String imageUrl;
        final String url;
        final String fallbackUrl;

        RichResult(String title, String subtitle, String imageUrl, String url) {
            this(title, subtitle, imageUrl, url, null);
        }

        RichResult(String title, String subtitle, String imageUrl, String url,
                String fallbackUrl) {
            this.title = title;
            this.subtitle = subtitle;
            this.imageUrl = imageUrl;
            this.url = url;
            this.fallbackUrl = fallbackUrl;
        }
    }

    private static final class ScoredResult {
        final UniversalSearchResult result;
        final int score;
        final String title;

        ScoredResult(UniversalSearchResult result, int score, String title) {
            this.result = result;
            this.score = score;
            this.title = title;
        }
    }
}
