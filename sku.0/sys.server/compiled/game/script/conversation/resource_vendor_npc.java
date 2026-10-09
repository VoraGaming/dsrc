package script.conversation;

import script.*;
import script.library.*;

public class resource_vendor_npc extends script.base_script
{
    public resource_vendor_npc()
    {
    }

    public static final String CONVO = "resource_vendor_npc";
    public static final String BRANCH_ID = "conversation.resource_vendor_npc.branchId";
    public static final String SELECTED_NAME = "conversation.resource_vendor_npc.selected_name";
    public static final String SELECTED_PRICE = "conversation.resource_vendor_npc.selected_price";
    public static final String RESOURCE_CLASS = "mineral";
    public static final String[] QUANTITIES = { "10", "50", "100", "1000" };

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        if ((!isMob(self)) || (isPlayer(self)))
        {
            detachScript(self, "conversation.resource_vendor_npc");
            return SCRIPT_CONTINUE;
        }
        setCondition(self, CONDITION_CONVERSABLE);
        setInvulnerable(self, true);
        setName(self, "Mineral Vendor");
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_SENTINEL);
        return SCRIPT_CONTINUE;
    }

    public int OnAttach(obj_id self) throws InterruptedException
    {
        setCondition(self, CONDITION_CONVERSABLE);
        setInvulnerable(self, true);
        setName(self, "Mineral Vendor");
        ai_lib.setDefaultCalmBehavior(self, ai_lib.BEHAVIOR_SENTINEL);
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info menuInfo) throws InterruptedException
    {
        int menu = menuInfo.addRootMenu(menu_info_types.CONVERSE_START, null);
        menu_info_data menuInfoData = menuInfo.getMenuItemById(menu);
        menuInfoData.setServerNotify(false);
        setCondition(self, CONDITION_CONVERSABLE);
        return SCRIPT_CONTINUE;
    }

    public int OnIncapacitated(obj_id self, obj_id killer) throws InterruptedException
    {
        clearCondition(self, CONDITION_CONVERSABLE);
        detachScript(self, "conversation.resource_vendor_npc");
        return SCRIPT_CONTINUE;
    }

    public int OnStartNpcConversation(obj_id self, obj_id player) throws InterruptedException
    {
        obj_id npc = self;
        if (ai_lib.isInCombat(npc) || ai_lib.isInCombat(player))
        {
            return SCRIPT_OVERRIDE;
        }
        presentList(player, npc);
        return SCRIPT_CONTINUE;
    }

    public int OnNpcConversationResponse(obj_id self, String conversationId, obj_id player, string_id response) throws InterruptedException
    {
        if (!conversationId.equals(CONVO))
        {
            return SCRIPT_CONTINUE;
        }
        obj_id npc = self;
        int branchId = utils.getIntScriptVar(player, BRANCH_ID);
        if (branchId == 1 && handleBranch1(player, npc, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        if (branchId == 2 && handleBranch2(player, npc, response) == SCRIPT_CONTINUE)
        {
            return SCRIPT_CONTINUE;
        }
        chat.chat(npc, "Error: fell through all branches for OnNpcConversationResponse.");
        utils.removeScriptVar(player, BRANCH_ID);
        return SCRIPT_CONTINUE;
    }

    public int handleResourcePurchase(obj_id self, dictionary params) throws InterruptedException
    {
        if (params == null)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id player = params.getObjId("player");
        obj_id resId = params.getObjId("resource");
        int qty = params.getInt("qty");
        int price = params.getInt("price");
        if (!isIdValid(player) || !isIdValid(resId) || (qty < 1))
        {
            return SCRIPT_CONTINUE;
        }
        if (params.getInt(money.DICT_CODE) == money.RET_FAIL)
        {
            utils.removeScriptVar(player, BRANCH_ID);
            npcEndConversationWithMessage(player, new string_id("You don't have enough credits for that."));
            return SCRIPT_CONTINUE;
        }
        obj_id inv = utils.getInventoryContainer(player);
        obj_id crate = createResourceCrate(resId, qty, inv);
        if (!isIdValid(crate))
        {
            npcEndConversationWithMessage(player, new string_id("The crate could not be delivered. Your inventory may be full."));
            return SCRIPT_CONTINUE;
        }
        obj_id[] looted = new obj_id[1];
        looted[0] = crate;
        showLootBox(player, looted);
        utils.removeScriptVar(player, BRANCH_ID);
        utils.removeScriptVar(player, SELECTED_NAME);
        utils.removeScriptVar(player, SELECTED_PRICE);
        npcEndConversation(player);
        return SCRIPT_CONTINUE;
    }

    // --- helpers ---

    private float configValue(String key, float fallback)
    {
        float value = dataTableGetFloat("systems/resource_vendor/pricing", key, "VALUE");
        if (value < 0.0f)
        {
            return fallback;
        }
        return value;
    }

    private int computeUnitPrice(obj_id resourceId)
    {
        float scale = configValue("scale", 1000.0f);
        if (scale <= 0.0f)
        {
            scale = 1000.0f;
        }
        float basePrice = configValue("basePrice", 10.0f);
        float capBonus = configValue("cap_bonus", 1.5f);
        float tier1Min = configValue("tier1_min", 0.90f);
        float tier1Bonus = configValue("tier1_bonus", 0.15f);
        float tier2Min = configValue("tier2_min", 0.95f);
        float tier2Bonus = configValue("tier2_bonus", 0.30f);
        float tier3Min = configValue("tier3_min", 0.99f);
        float tier3Bonus = configValue("tier3_bonus", 0.75f);

        resource_attribute[] attrs = getResourceAttributes(resourceId);
        if (attrs == null)
        {
            return 1;
        }
        float oq = 0.0f;
        float bonus = 0.0f;
        for (int i = 0; i < attrs.length; ++i)
        {
            resource_attribute attr = attrs[i];
            if ((attr == null) || (attr.getName() == null))
            {
                continue;
            }
            float pos = (float) attr.getValue() / scale;
            if (attr.getName().equals("res_quality"))
            {
                oq = pos;
                continue;
            }
            if (pos >= tier3Min)
            {
                bonus += tier3Bonus;
            }
            else if (pos >= tier2Min)
            {
                bonus += tier2Bonus;
            }
            else if (pos >= tier1Min)
            {
                bonus += tier1Bonus;
            }
        }
        if (bonus > capBonus)
        {
            bonus = capBonus;
        }
        float price = basePrice * oq * oq * (1.0f + bonus);
        int unitPrice = (int) Math.round(price);
        if (unitPrice < 1)
        {
            unitPrice = 1;
        }
        return unitPrice;
    }

    private resource_density[] getDepletedResources(obj_id player)
    {
        resource_density[] resources = requestResourceList(getLocation(player), 0.0f, 1.0f, RESOURCE_CLASS);
        if (resources == null)
        {
            return new resource_density[0];
        }
        int count = 0;
        for (int i = 0; i < resources.length; ++i)
        {
            if (isDepleted(resources[i]))
            {
                ++count;
            }
        }
        resource_density[] depleted = new resource_density[count];
        int idx = 0;
        for (int i = 0; i < resources.length; ++i)
        {
            if (isDepleted(resources[i]))
            {
                depleted[idx++] = resources[i];
            }
        }
        return depleted;
    }

    private boolean isDepleted(resource_density rd)
    {
        return (rd != null) && (rd.getDensity() == 0.0f) && isIdValid(rd.getResourceType());
    }

    private String resourceName(resource_density rd)
    {
        String name = getResourceName(rd.getResourceType());
        if (name == null)
        {
            name = "mineral";
        }
        return name;
    }

    private void presentList(obj_id player, obj_id npc) throws InterruptedException
    {
        resource_density[] list = getDepletedResources(player);
        if (list.length == 0)
        {
            utils.removeScriptVar(player, BRANCH_ID);
            npcEndConversationWithMessage(player, new string_id("I'm out of depleted minerals right now. Please come back later."));
            return;
        }
        string_id[] responses = new string_id[list.length + 1];
        for (int i = 0; i < list.length; ++i)
        {
            int price = computeUnitPrice(list[i].getResourceType());
            responses[i] = new string_id("Buy " + resourceName(list[i]) + " - " + price + " cr/unit");
        }
        responses[list.length] = new string_id("I'm done.");
        utils.setScriptVar(player, BRANCH_ID, 1);
        npcStartConversation(player, npc, CONVO, new string_id("I sell crates of depleted minerals from this planet. Which one do you want?"), responses);
    }

    private void presentQuantityPrompt(obj_id player, obj_id npc) throws InterruptedException
    {
        String name = utils.getStringScriptVar(player, SELECTED_NAME);
        int price = utils.getIntScriptVar(player, SELECTED_PRICE);
        if (name == null)
        {
            name = "minerals";
        }
        string_id[] responses = new string_id[QUANTITIES.length + 1];
        for (int i = 0; i < QUANTITIES.length; ++i)
        {
            responses[i] = new string_id("Buy " + QUANTITIES[i] + " (" + (price * Integer.parseInt(QUANTITIES[i])) + " cr)");
        }
        responses[QUANTITIES.length] = new string_id("Back to the list.");
        npcStartConversation(player, npc, CONVO, new string_id("How many units of " + name + "? Each one is " + price + " credits."), responses);
    }

    private int handleBranch1(obj_id player, obj_id npc, string_id response) throws InterruptedException
    {
        if (response.equals("I'm done."))
        {
            utils.removeScriptVar(player, BRANCH_ID);
            npcEndConversation(player);
            return SCRIPT_CONTINUE;
        }
        resource_density[] list = getDepletedResources(player);
        for (int i = 0; i < list.length; ++i)
        {
            int price = computeUnitPrice(list[i].getResourceType());
            if (response.equals("Buy " + resourceName(list[i]) + " - " + price + " cr/unit"))
            {
                utils.setScriptVar(player, BRANCH_ID, 2);
                utils.setScriptVar(player, SELECTED_NAME, resourceName(list[i]));
                utils.setScriptVar(player, SELECTED_PRICE, price);
                presentQuantityPrompt(player, npc);
                return SCRIPT_CONTINUE;
            }
        }
        utils.removeScriptVar(player, BRANCH_ID);
        npcEndConversationWithMessage(player, new string_id("I'm sorry, I don't have that one in stock anymore."));
        return SCRIPT_CONTINUE;
    }

    private int handleBranch2(obj_id player, obj_id npc, string_id response) throws InterruptedException
    {
        if (response.equals("Back to the list."))
        {
            utils.removeScriptVar(player, SELECTED_NAME);
            utils.removeScriptVar(player, SELECTED_PRICE);
            presentList(player, npc);
            return SCRIPT_CONTINUE;
        }
        String name = utils.getStringScriptVar(player, SELECTED_NAME);
        int price = utils.getIntScriptVar(player, SELECTED_PRICE);
        int qty = 0;
        for (int i = 0; i < QUANTITIES.length; ++i)
        {
            if (response.equals("Buy " + QUANTITIES[i] + " (" + (price * Integer.parseInt(QUANTITIES[i])) + " cr)"))
            {
                qty = Integer.parseInt(QUANTITIES[i]);
                break;
            }
        }
        if (qty < 1)
        {
            utils.removeScriptVar(player, BRANCH_ID);
            npcEndConversationWithMessage(player, new string_id("I'm sorry, I don't understand that."));
            return SCRIPT_CONTINUE;
        }
        if (name == null)
        {
            name = "";
        }
        resource_density[] list = getDepletedResources(player);
        obj_id resId = null;
        for (int i = 0; i < list.length; ++i)
        {
            if (name.equals(resourceName(list[i])))
            {
                resId = list[i].getResourceType();
                break;
            }
        }
        if (resId == null)
        {
            utils.removeScriptVar(player, BRANCH_ID);
            npcEndConversationWithMessage(player, new string_id("That stock just changed. Please start over."));
            return SCRIPT_CONTINUE;
        }
        int total = price * qty;
        dictionary params = new dictionary();
        params.put("player", player);
        params.put("resource", resId);
        params.put("qty", qty);
        params.put("price", total);
        money.requestPayment(player, npc, total, "handleResourcePurchase", params, false);
        return SCRIPT_CONTINUE;
    }
}
