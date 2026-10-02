package script.npc;

import script.*;
import script.library.buff;
import script.library.utils;

// Player-side session script for the Twi'lek entertainer buff-builder.
//
// Attached to the PLAYER (by conversation.entertainer_buff_npc) and drives the
// self-buff: the player is both buffer and recipient, so the player's own client
// shows the Build-a-Buff window. On completion the buff is applied to the player
// but its expertise is attributed to the NPC (the player has none), by
// pointing performance.buildabuff.bufferId at the NPC before applying.
//
// This class is also the single source of truth for the master-entertainer values,
// the client-marker contract, and the session cleanup, so both of the two
// same-named "entertainer_buff_npc" classes (script.npc and
// script.systems.buff_builder) can reference it without fully-qualified names.
public class entertainer_npc_session extends script.base_script
{
    public entertainer_npc_session()
    {
    }

    // Set on the player by the NPC conversation script: which NPC is buffing the player.
    public static final String VAR_NPC_ID = "performance.buildabuff.npcId";
    // True while a Build-a-Buff session is open (used by the NPC to refuse re-entry).
    public static final String VAR_IN_SESSION = "performance.buildabuff.inSession";

    // Read by systems.buff.buff_handler.buildabuffAddBuffHandler when the buff is applied.
    public static final String VAR_BUFF_KEYS = "performance.buildabuff.buffComponentKeys";
    public static final String VAR_BUFF_VALUES = "performance.buildabuff.buffComponentValues";
    public static final String VAR_BUFFER_ID = "performance.buildabuff.bufferId";

    public static final String BUILDABUFF_NAME = "buildabuff_inspiration";
    public static final float BUFF_DURATION = 3600.0f;       // exactly 1 hour
    public static final float MARKER_DURATION = 600.0f;      // safety net; markers are removed when the session ends
    public static final String DATATABLE_BUFF_BUILDER = "datatables/buff/buff_builder.iff";

    // Every entertainer starts with 8 points; expertise_en_inspire_base_point_increase adds to it.
    public static final int BASE_BUFF_POINTS = 8;

    // MASTER ENTERTAINER VALUES ("Build A", a dedicated level 90 buffer):
    //   base_point_increase 12 (8 + 12 = 20 point budget), attrib_increase 200, resist_increase 200,
    //   trader_increase 100, proc_chance_increase 4, combat_buff_increase 4, improv 0
    //   (improv stays 0 so the buff cannot dip to 20% strength).
    public static final String MOD_BASE_POINTS = "expertise_en_inspire_base_point_increase";
    public static final String[] MASTER_MOD_NAMES =
    {
        MOD_BASE_POINTS,
        "expertise_en_inspire_attrib_increase",
        "expertise_en_inspire_resist_increase",
        "expertise_en_inspire_trader_increase",
        "expertise_en_inspire_proc_chance_increase",
        "expertise_en_combat_buff_increase",
        "expertise_en_improv"
    };
    public static final int[] MASTER_MOD_VALUES = { 12, 200, 200, 100, 4, 4, 0 };

    // Client marker contract (agreed with the client side): every marker is added to the player
    // with addSkillModModifier(player, name, name, value, 600, false, false) and removed on cleanup.
    // bbnpc_master + one bbnpc_<mod> per master mod drive the 20-point window and the master bonuses;
    // the five bbnpc_expertise_en_* markers unlock the 7 expertise-gated buff_builder rows.
    public static final String MARKER_PREFIX = "bbnpc_";
    public static final String MARKER_MASTER = "bbnpc_master";
    public static final String[] MARKER_UNLOCK_NAMES =
    {
        "bbnpc_expertise_en_harvest_faire_1",
        "bbnpc_expertise_en_holism_1",
        "bbnpc_expertise_en_go_with_the_flow_1",
        "bbnpc_expertise_en_second_chance_1",
        "bbnpc_expertise_en_flush_with_success_1"
    };

    // A re-attach (login / server restart) must never leave a stale session on the player.
    public int OnInitialize(obj_id self) throws InterruptedException
    {
        cleanupSession(self);
        return SCRIPT_CONTINUE;
    }

    public int OnLogin(obj_id self) throws InterruptedException
    {
        cleanupSession(self);
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderValidate(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        // Self-buff: the player is both buffer and recipient.
        if (!isIdValid(bufferId) || !isIdValid(recipientId) || !bufferId.equals(self) || !recipientId.equals(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id npc = getSessionNpc(self);
        if (!accepted)
        {
            cleanupSession(self);
            return SCRIPT_CONTINUE;
        }
        if (!isNpcUsable(self, npc))
        {
            cancelSession(self, startingTime, buffComponentKeys, buffComponentValues);
            return SCRIPT_CONTINUE;
        }
        if (!areBuffChoicesValid(self, buffComponentKeys, buffComponentValues))
        {
            cancelSession(self, startingTime, buffComponentKeys, buffComponentValues);
            return SCRIPT_CONTINUE;
        }
        // Credits are always 0: the Entertainer NPC is free. The native RETURNS a boolean; dsrc has
        // no separate fail callback, so if it fails we clean up right here (the root cause of bug 6).
        boolean ok = buffBuilderValidated(self, self, startingTime, 0, 0, true, buffComponentKeys, buffComponentValues);
        if (!ok)
        {
            cleanupSession(self);
        }
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderCompleted(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        // Self-buff: the player is both buffer and recipient.
        if (!isIdValid(bufferId) || !isIdValid(recipientId) || !bufferId.equals(self) || !recipientId.equals(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id npc = getSessionNpc(self);
        if (!isIdValid(npc) || !exists(npc))
        {
            sendSystemMessage(self, "The Entertainer is no longer here. No buff was applied.", null);
            cleanupSession(self);
            return SCRIPT_CONTINUE;
        }
        if (buffComponentKeys == null || buffComponentValues == null || buffComponentKeys.length == 0 || buffComponentKeys.length != buffComponentValues.length)
        {
            sendSystemMessage(self, "No buffs were chosen, so nothing was applied.", null);
            cleanupSession(self);
            return SCRIPT_CONTINUE;
        }

        // The buff handler reads these when the buff is applied, so they MUST be set before applyBuff.
        // bufferId is the NPC: all expertise bonuses are read from the NPC's master skill mods.
        utils.setScriptVar(self, VAR_BUFF_KEYS, buffComponentKeys);
        utils.setScriptVar(self, VAR_BUFF_VALUES, buffComponentValues);
        utils.setScriptVar(self, VAR_BUFFER_ID, npc);

        // A new buff replaces the old one.
        if (buff.hasBuff(self, BUILDABUFF_NAME))
        {
            buff.removeBuff(self, BUILDABUFF_NAME);
        }
        buff.applyBuff(self, BUILDABUFF_NAME, BUFF_DURATION);
        sendSystemMessage(self, "You feel inspired! Your buffs will last 1 hour.", null);

        cleanupSession(self);
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderCanceled(obj_id self) throws InterruptedException
    {
        cleanupSession(self);
        return SCRIPT_CONTINUE;
    }

    // Puts the master skill mods on the NPC. applySkillStatisticModifier ADDS to the current value,
    // so we only add the difference; running this again (e.g. every restart) never stacks the values.
    public static void applyMasterMods(obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc))
        {
            return;
        }
        for (int i = 0; i < MASTER_MOD_NAMES.length; i++)
        {
            int current = getSkillStatMod(npc, MASTER_MOD_NAMES[i]);
            int difference = MASTER_MOD_VALUES[i] - current;
            if (difference != 0)
            {
                applySkillStatisticModifier(npc, MASTER_MOD_NAMES[i], difference);
            }
        }
    }

    // Adds the "bbnpc_*" marker skill mods to the player (client marker contract). The mod values
    // are read from the NPC with the same call the buff code uses, so the window always shows exactly
    // what the server will apply. Zero values are skipped (e.g. improv stays 0).
    public static void addClientMarkers(obj_id npc, obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        addMarker(player, MARKER_MASTER, 1);
        if (isIdValid(npc))
        {
            for (String modName : MASTER_MOD_NAMES)
            {
                int value = getSkillStatMod(npc, modName);
                addMarker(player, MARKER_PREFIX + modName, value);
            }
        }
        for (String markerName : MARKER_UNLOCK_NAMES)
        {
            addMarker(player, markerName, 1);
        }
    }

    public static void addMarker(obj_id player, String markerName, int value) throws InterruptedException
    {
        if (value == 0)
        {
            return;
        }
        if (hasSkillModModifier(player, markerName))
        {
            removeAttribOrSkillModModifier(player, markerName);
        }
        addSkillModModifier(player, markerName, markerName, value, MARKER_DURATION, false, false);
    }

    public static void removeClientMarkers(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        removeMarker(player, MARKER_MASTER);
        for (String modName : MASTER_MOD_NAMES)
        {
            removeMarker(player, MARKER_PREFIX + modName);
        }
        for (String markerName : MARKER_UNLOCK_NAMES)
        {
            removeMarker(player, markerName);
        }
    }

    public static void removeMarker(obj_id player, String markerName) throws InterruptedException
    {
        if (hasSkillModModifier(player, markerName))
        {
            removeAttribOrSkillModModifier(player, markerName);
        }
    }

    // Our own budget and cap checks so the player gets a clear message instead of a silent failure.
    // Budget = 8 (base) + the NPC's base_point_increase (12 with Build A) = 20.
    public boolean areBuffChoicesValid(obj_id player, String[] keys, int[] values) throws InterruptedException
    {
        if (keys == null || values == null || keys.length != values.length)
        {
            sendSystemMessage(player, "Those buff choices could not be read. Please try again.", null);
            return false;
        }
        int budget = BASE_BUFF_POINTS + getMasterValue(MOD_BASE_POINTS);
        int pointsSpent = 0;
        for (int i = 0; i < keys.length; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE_BUFF_BUILDER, keys[i]);
            if (row == null)
            {
                sendSystemMessage(player, "Unknown buff choice: " + keys[i], null);
                return false;
            }
            int maxTimes = row.getInt("MAX_TIMES_APPLIED");
            if (values[i] < 0 || values[i] > maxTimes)
            {
                sendSystemMessage(player, "Too many points in " + keys[i] + " (the most allowed is " + maxTimes + ").", null);
                return false;
            }
            pointsSpent += row.getInt("COST") * values[i];
        }
        if (pointsSpent > budget)
        {
            sendSystemMessage(player, "You chose " + pointsSpent + " points of buffs, but the limit is " + budget + ".", null);
            return false;
        }
        return true;
    }

    public static int getMasterValue(String modName) throws InterruptedException
    {
        for (int i = 0; i < MASTER_MOD_NAMES.length; i++)
        {
            if (MASTER_MOD_NAMES[i].equals(modName))
            {
                return MASTER_MOD_VALUES[i];
            }
        }
        return 0;
    }

    public boolean isNpcUsable(obj_id player, obj_id npc) throws InterruptedException
    {
        if (!isIdValid(npc) || !exists(npc))
        {
            sendSystemMessage(player, "The Entertainer is no longer here.", null);
            return false;
        }
        if (isDead(player) || isIncapacitated(player))
        {
            sendSystemMessage(player, "You cannot be inspired in your current condition.", null);
            return false;
        }
        return true;
    }

    public static obj_id getSessionNpc(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || !utils.hasScriptVar(player, VAR_NPC_ID))
        {
            return null;
        }
        return utils.getObjIdScriptVar(player, VAR_NPC_ID);
    }

    // Tells the engine the session is NOT accepted (closes the window). We also clean up right away
    // in case the OnBuffBuilderCanceled callback never arrives.
    public void cancelSession(obj_id player, int startingTime, String[] keys, int[] values) throws InterruptedException
    {
        if (keys == null)
        {
            keys = new String[0];
        }
        if (values == null)
        {
            values = new int[0];
        }
        buffBuilderValidated(player, player, startingTime, 0, 0, false, keys, values);
        cleanupSession(player);
    }

    // Removes the client markers, our scriptvars, and this script. Safe to call more than once.
    // Every exit path (validate-fail, completed, canceled, re-attach) funnels through here.
    public static void cleanupSession(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        removeClientMarkers(player);
        utils.removeScriptVar(player, VAR_NPC_ID);
        utils.removeScriptVar(player, VAR_IN_SESSION);
        // A real entertainer's /inspire session script, if a leftover copy is present, must not block /inspire.
        if (hasScript(player, "systems.buff_builder.buff_builder_cancel"))
        {
            detachScript(player, "systems.buff_builder.buff_builder_cancel");
        }
        if (hasScript(player, "npc.entertainer_npc_session"))
        {
            detachScript(player, "npc.entertainer_npc_session");
        }
    }
}
