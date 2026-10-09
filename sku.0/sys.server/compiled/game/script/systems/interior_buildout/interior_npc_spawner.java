package script.systems.interior_buildout;

/*
 * Attached to the cantina and medical centre base templates (base_cantina, base_hospital, base_hospital_02).
 * When the building loads, it spawns the rows of datatables/systems/interior_buildout/interior_npcs.iff
 * whose BUILDING column is this building's template, once (see interior_buildout_spawner).
 * A building with the objvar interior_buildout.disabled spawns nothing.
 */

import script.dictionary;
import script.obj_id;

public class interior_npc_spawner extends script.base_script
{
    public interior_npc_spawner()
    {
    }

    public static final String TABLE = "datatables/systems/interior_buildout/interior_npcs.iff";
    public static final String OBJVAR_DISABLED = "interior_buildout.disabled";
    // the building's cells must exist before anything can be put in them
    public static final float SPAWN_DELAY = 15.0f;

    public int OnAttach(obj_id self) throws InterruptedException
    {
        messageTo(self, "handleInteriorNpcSpawn", null, SPAWN_DELAY, false);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        messageTo(self, "handleInteriorNpcSpawn", null, SPAWN_DELAY, false);
        return SCRIPT_CONTINUE;
    }

    public int handleInteriorNpcSpawn(obj_id self, dictionary params) throws InterruptedException
    {
        if (!isIdValid(self) || hasObjVar(self, OBJVAR_DISABLED))
        {
            return SCRIPT_CONTINUE;
        }
        interior_buildout_spawner.startInteriorBuildoutSpawning(self, TABLE);
        return SCRIPT_CONTINUE;
    }
}
