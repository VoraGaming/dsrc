package script.tools.adv_conv_tool.examples;

import script.obj_id;
import script.tools.adv_conv_tool.advanced_conversation_tool;

import static script.tools.adv_conv_tool.adv_conv_utils.performRandomAnimation;

/**
 * Example conversation built with the advanced conversation tool.
 * Attach to any NPC: tools.adv_conv_tool.examples.incursion_informant_convo
 */
public class incursion_informant_convo extends advanced_conversation_tool {

    public incursion_informant_convo()
    {
    }

    @Override
    public int OnAttach(obj_id self) throws InterruptedException {
        return OnInitialize(self);
    }

    @Override
    public int OnInitialize(obj_id self) throws InterruptedException {

        setName(self, "Incursion Informant");

        return super.OnInitialize(self);
    }

    @Override
    public void initConversation(obj_id npc, obj_id player) throws InterruptedException {
        // Boilerplate setup (instance fields: one value per conversation script class)
        COMM_CONVO_BRANCH_ID = "conversation.tools.adv_conv_tool.examples.incursion_informant_convo.branchId";
        COMM_CONVO_ID = "incursion_informant_convo";

        setName(npc, "Incursion Informant");

        super.initConversation(npc, player);
    }

    @Override
    protected void initializeConversationTree(obj_id npc, obj_id player) throws InterruptedException {
        // NOTE: action lambdas must only use their own (_npc, _player) parameters. The script instance is shared,
        // so the npc/player of this method may belong to a different conversation by the time a lambda runs.

        // --- Entry Point ---
        // Always available if the NPC is conversable
        addEntryPoint((_npc, _player) -> true, 1);

        // --- Conversation Flow ---
        createBranch(1)
                .setNpcAnimation("wave_hail")
                .setNpcMessage("Good.  You've made it.  Please, no names.  Let's get down to business.  Your target has been " +
                        "identified and recon has already been done.  Here are the coordinates of the bunker.  Remember, it's " +
                        "not enough to eliminate all the enemies.  You must destroy the bunker and remove the foothold from " +
                        "these pirates.  Good luck.")

                // Option 1: Accept
                // The tool ends the conversation with the end message after running the action.
                .addExitResponse("Confirmed. Over and out.", "Excellent.  Now get on with it.  Move, move, move!", (_npc, _player) -> {
                    doAnimationAction(_npc, "wave_on_dismissing");
                    performRandomAnimation(_player);

                    // Send Mission Advance Signal
                    //dictionary params = new dictionary();
                    //params.put(OBJ_VAR_OBJ_MISSION, getObjIdObjVar(informantNPC, MISS_INCURSION_OBJ));
                    //params.put(OBJ_VAR_ADVANCE_MISSION_STATE, true); // Confirm
                    //messageTo(_player, "incursionMissionAdvanceState", params, 0, true);
                })

                // Option 2: Refuse
                // The tool handles the npcEndConversationWithMessage call using the second argument.
                .addExitResponse("Negative, I refuse this mission.", "That's a darn shame. Go on, get out of here.", (_npc, _player) -> {
                    doAnimationAction(_npc, "angry");
                    performRandomAnimation(_player);

                    // Send Mission Cancel Signal
                    //dictionary params = new dictionary();
                    //params.put(OBJ_VAR_OBJ_MISSION, getObjIdObjVar(informantNPC, MISS_INCURSION_OBJ));
                    //params.put(OBJ_VAR_ADVANCE_MISSION_STATE, false); // Abort
                    //messageTo(_player, "incursionMissionAdvanceState", params, 0, true);
                });
    }
}
