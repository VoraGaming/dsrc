package script.npc;

import script.*;
import script.library.*;

/**
 * Entertainer NPC - player side.
 *
 * This script is attached to the PLAYER by script.npc.entertainer_npc while the player's
 * Buff Builder window is open. The engine sends the Buff Builder callbacks to the "buffer",
 * and in our self-buff session the buffer is the player, so the callbacks arrive here.
 *
 * Flow:
 *  1. entertainerNpcStartSession  - about 1 s after the player talked to the NPC: opens the window.
 *  2. OnBuffBuilderValidate       - player pressed Accept: we check range and points, then tell the engine OK.
 *  3. OnBuffBuilderCompleted      - engine accepted: we apply the 1 hour buff, with the NPC as the buffer.
 *  4. OnBuffBuilderCanceled       - window closed or something failed: we clean up.
 *  5. entertainerNpcSessionTimeout - 10 minute safety net for abandoned windows.
 * Every step writes one "EntertainerNPC:" line to customerService.log for troubleshooting.
 * Every path ends in cleanupSession(), which removes the client markers and detaches this script.
 */
public class entertainer_npc_session extends script.base_script
{
    public entertainer_npc_session()
    {
    }

    public static final String DATATABLE_BUFF_BUILDER = "datatables/buff/buff_builder.iff";
    public static final String BUILDABUFF_NAME = "buildabuff_inspiration";

    // These scriptvar names are read by systems.buff.buff_handler.buildabuffAddBuffHandler.
    public static final String VAR_BUFF_KEYS = "performance.buildabuff.buffComponentKeys";
    public static final String VAR_BUFF_VALUES = "performance.buildabuff.buffComponentValues";
    public static final String VAR_BUFFER_ID = "performance.buildabuff.bufferId";

    // Leftover scripts are removed on login, like systems.buff_builder.buff_builder_response does.
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

    /**
     * Sent by the NPC (messageTo) about 1 second after the markers were added. Opens the window.
     */
    public int entertainerNpcStartSession(obj_id self, dictionary params) throws InterruptedException
    {
        obj_id npc = getSessionNpc(self);
        if (!isNpcUsable(self, npc))
        {
            entertainer_npc.csLog("session start failed (npc missing, player incapacitated or out of range) player=" + self);
            cleanupSession(self);
            return SCRIPT_CONTINUE;
        }
        // Self-buff: the player is both buffer and recipient, so their own client gets the builder window.
        buffBuilderStart(self, self);
        entertainer_npc.csLog("session started (window sent) player=" + self + " npc=" + npc);
        return SCRIPT_CONTINUE;
    }

    /**
     * Safety net sent by the NPC when the session was prepared. If the same session is still
     * open after 10 minutes, the window was abandoned: clean up. A newer session has a different
     * start time, so an old timer never touches it.
     */
    public int entertainerNpcSessionTimeout(obj_id self, dictionary params) throws InterruptedException
    {
        if (params == null || !utils.hasScriptVar(self, entertainer_npc.VAR_START_TIME))
        {
            return SCRIPT_CONTINUE;
        }
        int timerStartTime = params.getInt("startTime");
        int currentStartTime = utils.getIntScriptVar(self, entertainer_npc.VAR_START_TIME);
        if (timerStartTime != currentStartTime)
        {
            return SCRIPT_CONTINUE;
        }
        entertainer_npc.csLog("timeout cleanup player=" + self + " startTime=" + currentStartTime);
        cleanupSession(self);
        return SCRIPT_CONTINUE;
    }

    /**
     * The player pressed Accept. Check everything, then either confirm or cancel the session.
     */
    public int OnBuffBuilderValidate(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        // Only handle our own self-buff session.
        if (!isIdValid(bufferId) || !isIdValid(recipientId) || !bufferId.equals(self) || !recipientId.equals(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id npc = getSessionNpc(self);
        int keyCount = (buffComponentKeys == null) ? 0 : buffComponentKeys.length;
        entertainer_npc.csLog("validate player=" + self + " npc=" + npc + " accepted=" + accepted + " keys=" + keyCount + " points=" + countPoints(buffComponentKeys, buffComponentValues));
        if (!accepted)
        {
            entertainer_npc.csLog("validate: not accepted, cleaning up player=" + self);
            cleanupSession(self);
            return SCRIPT_CONTINUE;
        }
        if (!isNpcUsable(self, npc))
        {
            entertainer_npc.csLog("validate failed (npc missing, player incapacitated or out of range) player=" + self);
            cancelSession(self, startingTime, buffComponentKeys, buffComponentValues);
            return SCRIPT_CONTINUE;
        }
        if (!areBuffChoicesValid(self, buffComponentKeys, buffComponentValues))
        {
            entertainer_npc.csLog("validate failed (invalid buff choices) player=" + self);
            cancelSession(self, startingTime, buffComponentKeys, buffComponentValues);
            return SCRIPT_CONTINUE;
        }
        // Credits are always 0: the Entertainer NPC is free.
        boolean ok = buffBuilderValidated(self, self, startingTime, 0, 0, true, buffComponentKeys, buffComponentValues);
        entertainer_npc.csLog("validate: sent to engine player=" + self + " engineResult=" + ok);
        if (!ok)
        {
            // The engine could not find or finish the session; make sure nothing is left behind.
            cleanupSession(self);
        }
        return SCRIPT_CONTINUE;
    }

    /**
     * The engine accepted the session. Apply the buff for exactly 1 hour.
     */
    public int OnBuffBuilderCompleted(obj_id self, obj_id bufferId, obj_id recipientId, int startingTime, int bufferRequiredCredits, int recipientPaidCredits, boolean accepted, String[] buffComponentKeys, int[] buffComponentValues) throws InterruptedException
    {
        if (!isIdValid(bufferId) || !isIdValid(recipientId) || !bufferId.equals(self) || !recipientId.equals(self))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id npc = getSessionNpc(self);
        if (!isIdValid(npc) || !exists(npc))
        {
            entertainer_npc.csLog("completed but npc missing, no buff applied player=" + self);
            sendSystemMessage(self, "The Entertainer is no longer here. No buff was applied.", null);
            cleanupSession(self);
            return SCRIPT_CONTINUE;
        }
        if (buffComponentKeys == null || buffComponentValues == null || buffComponentKeys.length == 0 || buffComponentKeys.length != buffComponentValues.length)
        {
            entertainer_npc.csLog("completed with no buffs chosen player=" + self);
            sendSystemMessage(self, "No buffs were chosen, so nothing was applied.", null);
            cleanupSession(self);
            return SCRIPT_CONTINUE;
        }

        // The buff code reads these when the buff is applied, so they MUST be set before applyBuff.
        // bufferId is the NPC: all expertise bonuses are read from the NPC's master skill mods.
        utils.setScriptVar(self, VAR_BUFF_KEYS, buffComponentKeys);
        utils.setScriptVar(self, VAR_BUFF_VALUES, buffComponentValues);
        utils.setScriptVar(self, VAR_BUFFER_ID, npc);

        // A new buff replaces the old one.
        if (buff.hasBuff(self, BUILDABUFF_NAME))
        {
            buff.removeBuff(self, BUILDABUFF_NAME);
        }
        boolean applied = buff.applyBuff(self, BUILDABUFF_NAME, entertainer_npc.BUFF_DURATION);
        entertainer_npc.csLog("completed player=" + self + " npc=" + npc + " applied=" + applied + " duration=" + entertainer_npc.BUFF_DURATION + " keys=" + buffComponentKeys.length + " points=" + countPoints(buffComponentKeys, buffComponentValues));
        sendSystemMessage(self, "You feel inspired! Your buffs will last 1 hour.", null);

        cleanupSession(self);
        return SCRIPT_CONTINUE;
    }

    public int OnBuffBuilderCanceled(obj_id self) throws InterruptedException
    {
        entertainer_npc.csLog("canceled player=" + self);
        cleanupSession(self);
        return SCRIPT_CONTINUE;
    }

    /**
     * Our own version of the server's budget and cap checks, so the player gets a clear message
     * instead of a silent failure. Budget = 8 + the NPC's base point value (20 with Build A).
     */
    public boolean areBuffChoicesValid(obj_id player, String[] keys, int[] values) throws InterruptedException
    {
        if (keys == null || values == null || keys.length != values.length)
        {
            sendSystemMessage(player, "Those buff choices could not be read. Please try again.", null);
            return false;
        }
        int budget = entertainer_npc.BASE_BUFF_POINTS + getMasterValue(entertainer_npc.MOD_BASE_POINTS);
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

    /**
     * Adds up the point cost of the chosen buffs (for the log). Unknown rows count as 0.
     */
    public static int countPoints(String[] keys, int[] values) throws InterruptedException
    {
        if (keys == null || values == null || keys.length != values.length)
        {
            return -1;
        }
        int points = 0;
        for (int i = 0; i < keys.length; i++)
        {
            dictionary row = dataTableGetRow(DATATABLE_BUFF_BUILDER, keys[i]);
            if (row != null)
            {
                points += row.getInt("COST") * values[i];
            }
        }
        return points;
    }

    /**
     * Looks up a master value from the constants block in entertainer_npc.
     */
    public static int getMasterValue(String modName) throws InterruptedException
    {
        for (int i = 0; i < entertainer_npc.MASTER_MOD_NAMES.length; i++)
        {
            if (entertainer_npc.MASTER_MOD_NAMES[i].equals(modName))
            {
                return entertainer_npc.MASTER_MOD_VALUES[i];
            }
        }
        return 0;
    }

    /**
     * The NPC must still exist and be within range of the player.
     */
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
        if (getDistance(npc, player) > entertainer_npc.MAX_USE_RANGE)
        {
            sendSystemMessage(player, "You moved too far away from the Entertainer.", null);
            return false;
        }
        return true;
    }

    public static obj_id getSessionNpc(obj_id player) throws InterruptedException
    {
        if (!utils.hasScriptVar(player, entertainer_npc.VAR_NPC_ID))
        {
            return null;
        }
        return utils.getObjIdScriptVar(player, entertainer_npc.VAR_NPC_ID);
    }

    /**
     * Tells the engine the session is NOT accepted. The engine closes the window and sends
     * OnBuffBuilderCanceled. We also clean up right away in case that callback never arrives.
     */
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

    /**
     * Removes the client markers, our scriptvars, and this script. Safe to call more than once.
     * Also used by entertainer_npc to clear out an abandoned session.
     */
    public static void cleanupSession(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        entertainer_npc.removeClientMarkers(player);
        utils.removeScriptVar(player, entertainer_npc.VAR_NPC_ID);
        utils.removeScriptVar(player, entertainer_npc.VAR_START_TIME);
        // Newer sessions no longer attach this, but the first version of the Entertainer NPC did.
        // Remove it so a leftover copy can never block a real entertainer's /inspire.
        if (hasScript(player, entertainer_npc.SCRIPT_BUFF_BUILDER_CANCEL))
        {
            detachScript(player, entertainer_npc.SCRIPT_BUFF_BUILDER_CANCEL);
        }
        if (hasScript(player, entertainer_npc.SCRIPT_SESSION))
        {
            detachScript(player, entertainer_npc.SCRIPT_SESSION);
        }
    }
}
