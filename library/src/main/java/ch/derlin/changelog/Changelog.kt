package ch.derlin.changelog

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat.getLongVersionCode
import androidx.recyclerview.widget.RecyclerView
import ch.derlin.changelog.Changelog.getAppVersion
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.lang.Integer.parseInt
import java.sql.Date
import java.text.ParseException
import java.util.prefs.PreferenceChangeEvent
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * Changelog
 *
 * Copyright (C) 2018 Lucy Linder (derlin)
 *
 * This software may be modified and distributed under the terms
 * of the Apache 2.0 license.  See the LICENSE file for details.
 */
/**
 *  Updated to AndroidX: Copyright (C) 2019 Arne Rantzen (Tyxz)
 */

object Changelog {

    /** Use this value if you want all the changelog (i.e. all the release entries) to appear. */
    val ALL_VERSIONS = 0L

    /** Constants for xml tags and attributes (see res/xml/changelog.xml for an example) */
    object XmlTags {
        val RELEASE = "release"
        val ITEM = "change"
        val VERSION_NAME = "version"
        val VERSION_CODE = "versioncode"
        val SUMMARY = "summary"
        val DATE = "date"
        val TYPE ="type"
    }

    /**
     * Create a dialog displaying the changelog from last update.
     * @param ctx The calling activity
     */
    fun showDialogOnlyOnce(ctx: Activity, showOnFirstExecution: Boolean = false){
        //Show changelog dialog
        val version = ctx.getAppVersion()

        val pref = ChangeLogPreference(ctx)
        val lastVersion = pref.getVersion()
        if ((showOnFirstExecution && lastVersion == 0L) || (lastVersion > 0 && lastVersion < version.first)) {
            val dialog = createDialog(ctx, versionCode = lastVersion)
            dialog.setOnDismissListener({ _ -> pref.writeVersion(version.first) })
            dialog.show()
        }
    }

    /**
     * Create a dialog displaying the changelog.
     * @param ctx The calling activity
     * @param versionCode Define the oldest version to show. In other words, the dialog will contains
     * release entries with a `versionCode` attribute >= [versionCode]. Default to all.
     * @param title The title of the dialog. Default to "Changelog"
     * @param resId The resourceId of the xml file, default to `R.xml.changelog`
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun createDialog(ctx: Activity, versionCode: Long = ALL_VERSIONS,
                     title: String? = null, resId: Int = R.xml.changelog): AlertDialog {
        return AlertDialog.Builder(ctx, R.style.ChangeLogAlertDialogTheme)
                .setView(createChangelogView(ctx, versionCode, title, resId))
                .setPositiveButton("OK") { _, _ -> }
                .create()
    }

    /**
     * Create a custom view with the changelog list.
     * This is the view that is displayed in the dialog on a call to [createDialog].
     * See [createDialog] for the parameters.
     */
    @Throws(XmlPullParserException::class, IOException::class)
    fun createChangelogView(ctx: Activity, versionCode: Long = ALL_VERSIONS,
                            title: String? = null, resId: Int = R.xml.changelog): View {
        val view = ctx.layoutInflater.inflate(R.layout.changelog, null)
        val changelog = loadChangelog(ctx, resId, versionCode)
        title?.let { view.findViewById<TextView>(R.id.changelog_title).text = it }
        view.findViewById<RecyclerView>(R.id.recyclerview).adapter = ChangelogAdapter(changelog)
        return view
    }

    /**
     * Extension function to retrieve the current version of the application from the package.
     * @return a pair <versionName, versionCode> (as set in the build.gradle file). Example: <"1.1", 3>
     */
    @Throws(PackageManager.NameNotFoundException::class)
    fun Activity.getAppVersion(): Pair<Long, String> {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return Pair(getLongVersionCode(packageInfo), packageInfo.versionName)
    }

    // -----------------------------------------

    /**
     * Read the changelog.xml and create a list of [ChangelogItem] and [ChangelogHeader].
     * @param context: the calling activity
     * @param resourceId: the name of the changelog file, default to R.xml.changelog
     * @param version: the lowest release to display (see [createDialog] for more details)
     * @return the list of [ChangelogItem], in the order of the [resourceId] file (most to less recent)
     */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun loadChangelog(context: Activity, resourceId: Int = R.xml.changelog, version: Long = ALL_VERSIONS):
            MutableList<ChangelogItem> {
        val clList = mutableListOf<ChangelogItem>()
        val xml = context.resources.getXml(resourceId)
        try {
            while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == XmlTags.RELEASE) {
                    val releaseVersion = xml.getAttributeValue(null, XmlTags.VERSION_CODE).toLong()
                    clList.addAll(parseReleaseTag(context, xml))
                    if (releaseVersion <= version) break
                } else {
                    xml.next()
                }
            }
        } finally {
            xml.close()
        }
        return clList
    }

    /**
     * Parse one release tag attribute.
     * @param context the calling activity
     * @param xml the xml resource parser. Its cursor should be at a release tag.
     * @return a list containing one [ChangelogHeader] and zero or more [ChangelogItem]
     */
    @Throws(XmlPullParserException::class, IOException::class)
    private fun parseReleaseTag(context: Context, xml: XmlResourceParser): MutableList<ChangelogItem> {
        assert(xml.name == XmlTags.RELEASE && xml.eventType == XmlPullParser.START_TAG)
        val items = mutableListOf<ChangelogItem>()
        // parse header
        items.add(ChangelogHeader(
                version = xml.getAttributeValue(null, XmlTags.VERSION_NAME) ?: "X.X",
                date = xml.getAttributeValue(null, XmlTags.DATE)?.let {
                    parseDate(context, it)
                },
                summary = parseResource(context,xml.getAttributeValue(null, XmlTags.SUMMARY)))
        )
        xml.next()
        // parse changes
        var type = "default"
        var name: String? = xml.name
        while (xml.name == XmlTags.ITEM || xml.eventType== XmlPullParser.TEXT) {
            if(xml.eventType == XmlPullParser.START_TAG)
                type = xml.getAttributeValue(null, XmlTags.TYPE) ?: "default"
            else if(xml.eventType == XmlPullParser.END_TAG)
                name = null
            else if (xml.eventType == XmlPullParser.TEXT) {
                items.add(ChangelogItem(parseResource(context,xml.text)!!, type))
            }
            xml.next()
        }
        return items
    }

    /**
     * Format a date string.
     * @param context The calling activity
     * @param dateString The date string, in ISO format (YYYY-MM-dd)
     * @return The date formatted using the system locale, or [dateString] if the parsing failed.
     */
    private fun parseDate(context: Context, dateString: String): String {
        try {
            val parsedDate = Date.valueOf(dateString)
            return DateFormat.getDateFormat(context).format(parsedDate)
        } catch (_: ParseException) {
            // wrong date format... Just keep the string as is
            return dateString
        }
    }

    /**
     * Recursively replaces resources such as `@string/abc` with
     * their localized values from the app's resource strings (e.g.
     * `strings.xml`) within a `source` string.
     *
     * Also works recursively, that is, when a resource contains another
     * resource that contains another resource, etc.
     *
     * @param source
     * @return `source` with replaced resources (if they exist)
     */
    private fun parseResource(ctx: Context, source: String?) : String? {
        if (source?.startsWith("@") != true)
            return source

        if (!source.startsWith("@string")) {
            try {
                val resourceId = parseInt(source.substring(1))
                return ctx.getString(resourceId)
            } catch (e: NumberFormatException) {
                Log.w(ctx.packageName,
                        "No String resource found for ID \"${source}\" while inserting resources")
            }
            return null
        }

         val REGEX_RESOURCE_STRING = "@string/([A-Za-z0-9-_]*)"

        val p: Pattern = Pattern.compile(REGEX_RESOURCE_STRING)
        val m: Matcher = p.matcher(source)
        val sb = StringBuffer()
        while (m.find()) {
            val srcName = m.group(1) ?: continue
                var stringFromResources = getStringByName(ctx, srcName)
                if (stringFromResources == null) {
                    Log.w( ctx.packageName,
                            "No String resource found for ID \"${srcName.toString()}\" while inserting resources")
                    /*
                 * No need to try to load from defaults, android is trying that
                 * for us. If we're here, the resource does not exist. Just
                 * return its ID.
                 */
                    stringFromResources = srcName
                }
                m.appendReplacement(sb,  // Recurse
                        parseResource(ctx, stringFromResources))
        }
        m.appendTail(sb)
        return sb.toString()
    }

    /**
     * Returns the string value of a string resource (e.g. defined in
     * <code>values.xml</code>).
     *
     * @param name
     * @return the value of the string resource or <code>null</code> if no
     *         resource found for id
     */
    private fun getStringByName(ctx: Context, name: String?): String? {
        val resourceId = getResourceId(ctx, "string", name)
        return if (resourceId != 0) {
            ctx.getString(resourceId)
        } else {
            null
        }
    }

    /**
     * Finds the numeric id of a string resource (e.g. defined in
     * <code>values.xml</code>).
     *
     * @param defType
     *            Optional default resource type to find, if "type/" is not
     *            included in the name. Can be null to require an explicit type.
     *
     * @param name
     *            the name of the desired resource
     * @return the associated resource identifier. Returns 0 if no such resource
     *         was found. (0 is not a valid resource ID.)
     */
    private fun getResourceId(context: Context, defType: String,
                              name: String?): Int {
        return context.resources.getIdentifier(name, defType,
                context.packageName)
    }

    /**
     * Control change log preference
     */
    class ChangeLogPreference(val ctx: Context) {
        private val PREF_ID = "chanegLogPref"
        private val ACTUAL_VERSION_PREF = "lastVersion"

        private fun prefs(): SharedPreferences {
            return ctx.getSharedPreferences(PREF_ID, 0)
        }

        fun writeVersion(value: Long){
            prefs().edit().run {
                putLong(ACTUAL_VERSION_PREF, value)
            }.apply()
        }

        fun getVersion() = prefs().getLong(ACTUAL_VERSION_PREF, 0)
    }


}


