package script.npc;

import script.*;
import script.library.*;

/**
 * Entertainer NPC.
 *
 * When a player talks to this NPC, the game's real entertainer Buff Builder window opens for them.
 * The player picks buffs (up to a 20 point budget) and presses Accept. The buffs are applied for
 * exactly 1 hour, free of charge, and replace any entertainer buff the player already has.
 *
 * How it works (the "self-buff" trick):
 *  - An NPC has no game client, so it cannot be the "buffer" in a Buff Builder session.
 *    Instead the session is started as player-buffs-player: buffBuilderStart(player, player).
 *  - The session callbacks (OnBuffBuilderValidate / Completed / Canceled) fire on the PLAYER,
 *    so this script attaches "npc.entertainer_npc_session" to the player to handle them.
 *  - The buff effect code (systems.buff.buff_handler.buildabuffAddBuffHandler) reads all the
 *    expertise bonuses from whatever object is stored in the player's scriptvar
 *    "performance.buildabuff.bufferId". The session script stores THIS NPC there, and this NPC
 *    carries the master entertainer skill mods below. So every player gets the same master buff,
 *    no matter what their own skills are.
 *  - While the window is open, the player gets short-lived "bbnpc_*" marker skill mods. The client
 *    uses them to show the window in "master mode" (20 points, master bonuses, all rows unlocked).
 *
 * Spawn it with:  /object createAt object/mobile/entertainer_npc.iff
 * Place it OUTDOORS - objects saved inside pre-placed city buildings do not reload after a restart.
 */
public class entertainer_npc extends script.base_script
{
    public entertainer_npc()
    {
    }

    public static final String NPC_NAME = "Entertainer";
    public static final String SCRIPT_SESSION = "npc.entertainer_npc_session";

    // These two scripts belong to the normal entertainer /inspire flow. If either is on the player,
    // a real entertainer session is already running and we must not start a second one.
    public static final String SCRIPT_BUFF_BUILDER_RESPONSE = "systems.buff_builder.buff_builder_response";
    public static final String SCRIPT_BUFF_BUILDER_CANCEL = "systems.buff_builder.buff_builder_cancel";

    public static final float MAX_USE_RANGE = 10.0f;
    public static final float BUFF_DURATION = 3600.0f;       // exactly 1 hour
    public static final float MARKER_DURATION = 600.0f;      // safety net only; markers are removed when the window closes
    public static final float SESSION_START_DELAY = 1.0f;    // gives the client time to receive the markers first
    public static final float SESSION_TIMEOUT_SECONDS = 600.0f; // an unfinished session is cleaned up after this long
    public static final int DOUBLE_CLICK_SECONDS = 3;        // talking again within this time is ignored (double-click guard)

    // Every entertainer starts with 8 points; expertise_en_inspire_base_point_increase adds to it.
    public static final int BASE_BUFF_POINTS = 8;

    // Scriptvars kept on the PLAYER while a session is running.
    public static final String VAR_NPC_ID = "entertainer_npc.npcId";
    public static final String VAR_START_TIME = "entertainer_npc.startTime";

    // ---------------------------------------------------------------------------------------------
    // MASTER ENTERTAINER VALUES ("Build A", a dedicated level 90 buffer: 29 of 45 expertise points).
    //   creativity 4        -> base_point_increase 12  (8 + 12 = 20 point budget)
    //   inspired_fitness 4  -> attrib_increase 200     (attributes x3)
    //   inspired_resilience 4 -> resist_increase 200   (resistances x3)
    //   inspired_industry 4 -> trader_increase 100     (trade x2)
    //   inspired_reactions 4 -> proc_chance_increase 4
    //   inspired_warfare 4  -> combat_buff_increase 4
    //   improv is NOT taken (0). With improv the buff can drop to 20% strength, so keep it 0.
    // Change a number here and both the NPC and the client markers follow it.
    // ---------------------------------------------------------------------------------------------
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
    public static final int[] MASTER_MOD_VALUES =
    {
        12,
        200,
        200,
        100,
        4,
        4,
        0
    };

    // Client marker contract (agreed with the client side). Every marker is added to the player
    // with addSkillModModifier(player, name, name, value, 600, false, false) and removed when done.
    public static final String MARKER_PREFIX = "bbnpc_";
    public static final String MARKER_MASTER = "bbnpc_master";
    // The expertise boxes that unlock the 7 expertise-gated buff_builder.tab rows (all taken in Build A).
    public static final String[] MARKER_UNLOCK_NAMES =
    {
        "bbnpc_expertise_en_harvest_faire_1",
        "bbnpc_expertise_en_holism_1",
        "bbnpc_expertise_en_go_with_the_flow_1",
        "bbnpc_expertise_en_second_chance_1",
        "bbnpc_expertise_en_flush_with_success_1"
    };

    public int OnAttach(obj_id self) throws InterruptedException
    {
        setupEntertainerNpc(self);
        return SCRIPT_CONTINUE;
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        setupEntertainerNpc(self);
        return SCRIPT_CONTINUE;
    }

    public void setupEntertainerNpc(obj_id self) throws InterruptedException
    {
        setName(self, NPC_NAME);
        setInvulnerable(self, true);
        setCondition(self, CONDITION_CONVERSABLE);
        applyMasterMods(self);
        // Loop the dance from spawn (same mood the NPE blurts use for /dance).
        setAnimationMood(self, "npc_dance_basic");
    }

    // Re-assert the dance when the object enters the world, so the loop survives
    // any client (re)load. OnAttach/OnInitialize already call setupEntertainerNpc.
    public int OnObjectEnterWorld(obj_id self) throws InterruptedException
    {
        setAnimationMood(self, "npc_dance_basic");
        return SCRIPT_CONTINUE;
    }

    /**
     * Puts the master skill mods on the NPC.
     * applySkillStatisticModifier ADDS to the current value, so we only add the difference
     * between what the NPC has now and what it should have. That way running this again
     * (every server restart calls OnInitialize) never stacks the values up.
     */
    public static void applyMasterMods(obj_id npc) throws InterruptedException
    {
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

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        int mnu = mi.addRootMenu(menu_info_types.CONVERSE_START, null);
        menu_info_data mdata = mi.getMenuItemById(mnu);
        mdata.setServerNotify(false);
        setCondition(self, CONDITION_CONVERSABLE);
        return SCRIPT_CONTINUE;
    }

    public int OnStartNpcConversation(obj_id self, obj_id speaker) throws InterruptedException
    {
        if (isIdValid(speaker) && isPlayer(speaker))
        {
            startBuffSession(self, speaker);
        }
        return SCRIPT_CONTINUE;
    }

    /**
     * Checks the player can be buffed, gives them the client markers, and asks the player's
     * session script to open the Buff Builder window about 1 second later.
     */
    public void startBuffSession(obj_id self, obj_id player) throws InterruptedException
    {
        csLog("talk player=" + player + " npc=" + self);
        if (isDead(player) || isIncapacitated(player))
        {
            csLog("refused (dead or incapacitated) player=" + player);
            sendSystemMessage(player, "You cannot be inspired in your current condition.", null);
            return;
        }
        if (getDistance(self, player) > MAX_USE_RANGE)
        {
            csLog("refused (too far) player=" + player);
            sendSystemMessage(player, "You are too far away from the Entertainer.", null);
            return;
        }
        if (hasScript(player, SCRIPT_SESSION))
        {
            // Only a very recent session is protected, so a double-click doesn't open two windows.
            int startTime = utils.getIntScriptVar(player, VAR_START_TIME);
            if (getGameTime() - startTime < DOUBLE_CLICK_SECONDS)
            {
                csLog("refused (double click) player=" + player);
                sendSystemMessage(player, "Please wait a moment, your window is opening.", null);
                return;
            }
            // An older session is still attached (for example the window was closed after Accept,
            // which tells the server nothing). Throw it away and start fresh. Starting a new
            // Buff Builder session for the same player replaces the old one in the engine too.
            csLog("replacing old session player=" + player);
            entertainer_npc_session.cleanupSession(player);
        }
        if (hasScript(player, SCRIPT_BUFF_BUILDER_RESPONSE) || hasScript(player, SCRIPT_BUFF_BUILDER_CANCEL))
        {
            // A real entertainer's /inspire session is running for this player.
            csLog("refused (real entertainer session running) player=" + player);
            sendSystemMessage(player, "You are already being inspired. Finish or cancel that first.", null);
            return;
        }

        // Make sure the master values are really on the NPC before we copy them to the player.
        applyMasterMods(self);

        // Greet before the window opens: face the player and say one line to everyone nearby.
        faceTo(self, player);
        chat._chat(self, player, chat.CHAT_SAY, null, "Hi Honey! How can I inspire you today?", null, null);

        int startTime = getGameTime();
        utils.setScriptVar(player, VAR_NPC_ID, self);
        utils.setScriptVar(player, VAR_START_TIME, startTime);
        attachScript(player, SCRIPT_SESSION);

        addClientMarkers(self, player);
        messageTo(player, "entertainerNpcStartSession", null, SESSION_START_DELAY, false);

        // Safety net: if the window is abandoned without the server hearing about it,
        // clean up after 10 minutes. The handler only acts if this is still the same session.
        dictionary timeoutParams = new dictionary();
        timeoutParams.put("startTime", startTime);
        messageTo(player, "entertainerNpcSessionTimeout", timeoutParams, SESSION_TIMEOUT_SECONDS, false);

        csLog("session prepared player=" + player + " npc=" + self + " startTime=" + startTime);
        sendSystemMessage(player, "Choose your inspiration buffs, then press Accept. They will last 1 hour.", null);
    }

    /**
     * Writes one line to customerService.log. On this server only the "CustomerService"
     * log channel reaches a readable file, so all Entertainer NPC diagnostics go there.
     */
    public static void csLog(String message) throws InterruptedException
    {
        CustomerServiceLog("CustomerService", "EntertainerNPC: " + message);
    }

    /**
     * Adds the "bbnpc_*" marker skill mods to the player (client marker contract).
     * The numbers are read from the NPC with the same call the buff code uses, so the
     * window always shows exactly what the server will apply. Zero values are skipped.
     */
    public static void addClientMarkers(obj_id npc, obj_id player) throws InterruptedException
    {
        addMarker(player, MARKER_MASTER, 1);
        for (String modName : MASTER_MOD_NAMES)
        {
            int value = getEnhancedSkillStatisticModifierUncapped(npc, modName);
            addMarker(player, MARKER_PREFIX + modName, value);
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

    /**
     * Removes every marker the NPC could have added. Safe to call more than once.
     */
    public static void removeClientMarkers(obj_id player) throws InterruptedException
    {
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
}
