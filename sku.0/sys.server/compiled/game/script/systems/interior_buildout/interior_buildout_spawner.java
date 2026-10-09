package script.systems.interior_buildout;

/*
 * Interior buildout spawner.
 *
 * Original idea and first version: interior_buildout_spawner.java (package script.bmcstudios.util),
 * shared with this project by another developer; credit to its author.
 * Reworked for this server: one spawn per row per building (no duplicates when it runs again),
 * rows filtered by building template, cell names checked, NPCs made safe to stand in a room,
 * and missing columns given defaults instead of throwing.
 *
 * Datatable (tab separated, row 2 = types). Every column except TEMPLATE and CELL_NAME is optional:
 *
 * BUILDING     server template of the building the row is for, e.g. object/building/tatooine/cantina_tatooine.iff
 *              (leave the column out to spawn every row in whatever building calls this)
 * TEMPLATE     object template (ends in .iff, created with createObject) or a creatures.tab name (created
 *              through library.create, so it gets its stats and AI)
 * CELL_NAME    room name from the building's portal layout (python tools/swg-assets/interior.py cells <building>)
 * POS_X POS_Y POS_Z   cell-local position in metres (POS_Y = floor height there; interior.py check gives it)
 * YAW PITCH ROLL      degrees; pitch and roll are ignored for creatures
 * DETACH_SCRIPTS ATTACH_SCRIPTS   comma separated script names, or none
 * OBJ_VARS     utils.setObjVarsList format: string:key=value,int:key2=5,stringarray:k=a|b|c   (or none)
 * NAME         name to give the object (or none)
 * STATIC       creatures: 1 = stay on the spot (default), 0 = may wander
 * INVULNERABLE creatures: 1 = can't be attacked (default), 0 = normal
 *
 * Positions must be checked before they go in a table:
 *   python tools/swg-assets/interior.py checktable <the .tab>
 */

import script.dictionary;
import script.library.create;
import script.library.utils;
import script.location;
import script.obj_id;

public class interior_buildout_spawner extends script.base_script
{
    public interior_buildout_spawner()
    {
    }

    public static final String LOG_CHANNEL = "interior_buildout";

    // on the building: the object spawned for each row (interior_buildout.spawned.<table crc>.<row>)
    public static final String OBJVAR_SPAWNED = "interior_buildout.spawned";
    // on each spawned object: which table row made it, and the building it belongs to
    public static final String OBJVAR_SOURCE = "interior_buildout.source";
    public static final String OBJVAR_BUILDING = "interior_buildout.building";

    public static final String DT_BUILDING = "BUILDING";
    public static final String DT_TEMPLATE = "TEMPLATE";
    public static final String DT_CELL_NAME = "CELL_NAME";
    public static final String DT_POS_X = "POS_X";
    public static final String DT_POS_Y = "POS_Y";
    public static final String DT_POS_Z = "POS_Z";
    public static final String DT_YAW = "YAW";
    public static final String DT_PITCH = "PITCH";
    public static final String DT_ROLL = "ROLL";
    public static final String DT_DETACH_SCRIPTS = "DETACH_SCRIPTS";
    public static final String DT_ATTACH_SCRIPTS = "ATTACH_SCRIPTS";
    public static final String DT_OBJ_VARS = "OBJ_VARS";
    public static final String DT_NAME = "NAME";
    public static final String DT_STATIC = "STATIC";
    public static final String DT_INVULNERABLE = "INVULNERABLE";

    /**
     * Spawns every row of the datatable that belongs to this building, once.
     * Safe to call again (e.g. on every OnInitialize): rows whose object still exists are skipped,
     * and an object from an earlier run that is still in the cell is reused instead of duplicated.
     * @return the number of objects created by this call
     */
    public static int startInteriorBuildoutSpawning(obj_id building, String datatable) throws InterruptedException
    {
        if (!isIdValid(building) || datatable == null || datatable.length() == 0)
        {
            return 0;
        }
        int tableRows = dataTableGetNumRows(datatable);
        if (tableRows <= 0)
        {
            LOG(LOG_CHANNEL, "no rows in " + datatable + " (missing table?) for building " + building);
            return 0;
        }
        String buildingTemplate = getTemplateName(building);
        boolean filterByBuilding = dataTableHasColumn(datatable, DT_BUILDING);
        String planet = getLocation(building).area;
        String tableKey = OBJVAR_SPAWNED + "." + getStringCrc(datatable);
        int created = 0;
        for (int row = 0; row < tableRows; row++)
        {
            dictionary item = dataTableGetRow(datatable, row);
            if (item == null)
            {
                continue;
            }
            if (filterByBuilding && !text(item, DT_BUILDING, "").equalsIgnoreCase(buildingTemplate))
            {
                continue;
            }
            String template = text(item, DT_TEMPLATE, "");
            String cellName = text(item, DT_CELL_NAME, "");
            String where = datatable + " row " + (row + 1) + " (" + template + " in " + buildingTemplate + " / " + cellName + ")";
            if (template.length() == 0 || cellName.length() == 0)
            {
                LOG(LOG_CHANNEL, where + ": TEMPLATE and CELL_NAME are required; row skipped");
                continue;
            }
            String source = datatable + ":" + row;
            String spawnedKey = tableKey + "." + row;

            // 1. already spawned and still there?
            if (hasObjVar(building, spawnedKey))
            {
                obj_id previous = getObjIdObjVar(building, spawnedKey);
                if (isIdValid(previous) && exists(previous))
                {
                    continue;
                }
                removeObjVar(building, spawnedKey);
            }

            // 2. the cell must exist in this building
            obj_id cell = getCellId(building, cellName);
            if (!isIdValid(cell))
            {
                LOG(LOG_CHANNEL, where + ": this building has no cell '" + cellName + "'; row skipped");
                continue;
            }

            // 3. left over from an earlier run (e.g. a persisted building)? reuse it
            obj_id leftover = findLeftover(cell, source, template);
            if (isIdValid(leftover))
            {
                setObjVar(building, spawnedKey, leftover);
                continue;
            }

            // 4. create it
            location itemLocation = new location(number(item, DT_POS_X, 0.0f), number(item, DT_POS_Y, 0.0f), number(item, DT_POS_Z, 0.0f), planet, cell);
            obj_id itemObj;
            if (template.endsWith(".iff"))
            {
                itemObj = createObject(template, itemLocation);
            }
            else
            {
                itemObj = create.object(template, itemLocation);
            }
            if (!isIdValid(itemObj))
            {
                LOG(LOG_CHANNEL, where + ": could not create the object (wrong template or creature name?)");
                continue;
            }
            created++;
            setObjVar(itemObj, OBJVAR_SOURCE, source);
            setObjVar(itemObj, OBJVAR_BUILDING, building);
            setObjVar(building, spawnedKey, itemObj);

            setYaw(itemObj, number(item, DT_YAW, 0.0f));
            boolean creature = isMob(itemObj);
            if (!creature)
            {
                float pitch = number(item, DT_PITCH, 0.0f);
                float roll = number(item, DT_ROLL, 0.0f);
                if (pitch != 0.0f)
                {
                    modifyPitch(itemObj, pitch);
                }
                if (roll != 0.0f)
                {
                    modifyRoll(itemObj, roll);
                }
            }
            else
            {
                if (whole(item, DT_INVULNERABLE, 1) != 0)
                {
                    setInvulnerable(itemObj, true);
                }
                if (whole(item, DT_STATIC, 1) != 0)
                {
                    setCreatureStatic(itemObj, true);
                }
            }

            String name = text(item, DT_NAME, "none");
            if (!isNone(name))
            {
                setName(itemObj, name);
            }

            String objectVariables = text(item, DT_OBJ_VARS, "none");
            if (!isNone(objectVariables))
            {
                utils.setObjVarsList(itemObj, objectVariables);
            }

            for (String script : text(item, DT_DETACH_SCRIPTS, "none").split(","))
            {
                script = script.trim();
                if (!isNone(script) && hasScript(itemObj, script))
                {
                    detachScript(itemObj, script);
                }
            }
            for (String script : text(item, DT_ATTACH_SCRIPTS, "none").split(","))
            {
                script = script.trim();
                if (!isNone(script) && !hasScript(itemObj, script))
                {
                    attachScript(itemObj, script);
                }
            }

            handleObjectSpawningFixes(itemObj);
        }
        if (created > 0)
        {
            LOG(LOG_CHANNEL, "spawned " + created + " object(s) from " + datatable + " in " + buildingTemplate + " " + building);
        }
        return created;
    }

    /** Destroys everything this table spawned in the building (for testing or before moving rows). */
    public static int cleanupInteriorBuildout(obj_id building, String datatable) throws InterruptedException
    {
        int removed = 0;
        obj_id[] cells = getCellIds(building);
        if (cells == null)
        {
            return 0;
        }
        for (obj_id cell : cells)
        {
            obj_id[] contents = getContents(cell);
            if (contents == null)
            {
                continue;
            }
            for (obj_id content : contents)
            {
                if (isIdValid(content) && hasObjVar(content, OBJVAR_SOURCE) && getStringObjVar(content, OBJVAR_SOURCE).startsWith(datatable + ":"))
                {
                    destroyObject(content);
                    removed++;
                }
            }
        }
        removeObjVar(building, OBJVAR_SPAWNED + "." + getStringCrc(datatable));
        return removed;
    }

    private static obj_id findLeftover(obj_id cell, String source, String template) throws InterruptedException
    {
        obj_id[] contents = getContents(cell);
        if (contents == null)
        {
            return null;
        }
        for (obj_id content : contents)
        {
            if (isIdValid(content) && hasObjVar(content, OBJVAR_SOURCE) && source.equals(getStringObjVar(content, OBJVAR_SOURCE)))
            {
                return content;
            }
        }
        return null;
    }

    private static String text(dictionary item, String column, String fallback)
    {
        if (!item.containsKey(column))
        {
            return fallback;
        }
        String value = item.getString(column);
        return value == null ? fallback : value.trim();
    }

    private static float number(dictionary item, String column, float fallback)
    {
        return item.containsKey(column) ? item.getFloat(column) : fallback;
    }

    private static int whole(dictionary item, String column, int fallback)
    {
        return item.containsKey(column) ? item.getInt(column) : fallback;
    }

    private static boolean isNone(String value)
    {
        return value == null || value.length() == 0 || value.equalsIgnoreCase("none");
    }

    /**
     * When we spawn an interior object, we sometimes have to adjust some properties of the object so the object behaves
     * correctly. The specific objects will be in a switch statement by template, and we can add the fixes in this function.
     */
    private static void handleObjectSpawningFixes(obj_id itemObj) throws InterruptedException
    {
        String templateName = getTemplateName(itemObj);
        String planetId = getLocation(itemObj).area;
        switch (templateName)
        {
            // Bank Terminal
            case "object/tangible/terminal/terminal_bank.iff":
                // The bank terminals need to have the correct planet IDs associated with them in order for the safety
                // deposit to function properly. If the player drops their Storage Hut onto planet Tatooine, then the
                // banking_bankid must be 'tatooine'.
                if (planetId != null && !planetId.isEmpty())
                {
                    setObjVar(itemObj, "banking_bankid", planetId);
                }
                break;
        }
    }
}
