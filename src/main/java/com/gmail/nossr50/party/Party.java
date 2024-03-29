package com.gmail.nossr50.party;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.gmail.nossr50.Users;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.datatypes.PlayerProfile;
import com.gmail.nossr50.locale.mcLocale;

public class Party {
    /*
     * This file is part of mmoMinecraft (http://code.google.com/p/mmo-minecraft/).
     * 
     * mmoMinecraft is free software: you can redistribute it and/or modify
     * it under the terms of the GNU General Public License as published by
     * the Free Software Foundation, either version 3 of the License, or
     * (at your option) any later version.
     *
     * This program is distributed in the hope that it will be useful,
     * but WITHOUT ANY WARRANTY; without even the implied warranty of
     * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     * GNU General Public License for more details.

     * You should have received a copy of the GNU General Public License
     * along with this program.  If not, see <http://www.gnu.org/licenses/>.
     */

    public static String partyPlayersFile = mcMMO.maindirectory + File.separator + "FlatFileStuff" + File.separator + "partyPlayers";
    public static String partyLocksFile = mcMMO.maindirectory + File.separator + "FlatFileStuff" + File.separator + "partyLocks";
    public static String partyPasswordsFile = mcMMO.maindirectory + File.separator + "FlatFileStuff" + File.separator + "partyPasswords";

    HashMap<String, HashMap<String, Boolean>> partyPlayers = new HashMap<String, HashMap<String, Boolean>>();
    HashMap<String, Boolean> partyLocks = new HashMap<String, Boolean>();
    HashMap<String, String> partyPasswords = new HashMap<String, String>();

    private static mcMMO plugin;
    private static volatile Party instance;

    public Party(mcMMO instance) {
        new File(mcMMO.maindirectory + File.separator + "FlatFileStuff").mkdir();
        plugin = instance;
    }

    public static Party getInstance() {
        if (instance == null) {
            instance = new Party(plugin);
        }
        return instance;
    }

    /**
     * Check if two players are in the same party.
     *
     * @param playera The first player
     * @param playerb The second player
     * @return true if they are in the same party, false otherwise
     */
    public boolean inSameParty(Player playera, Player playerb){
        PlayerProfile PPa = Users.getProfile(playera);
        PlayerProfile PPb = Users.getProfile(playerb);

        if ((PPa.inParty() && PPb.inParty()) && (PPa.getParty().equals(PPb.getParty()))) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Get the number of players in this player's party.
     *
     * @param player The player to check
     * @param players A list of players to 
     * @return the number of players in this player's party
     */
    public int partyCount(Player player) {
        PlayerProfile PP = Users.getProfile(player);
        int partyMembers = 0;

        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            if (player != null && p != null) { //Is this even possible?
                if (PP.getParty().equals(Users.getProfile(p).getParty())) {
                    partyMembers++;
                }
            }
        }

        return partyMembers;
    }

    private void informPartyMembers(Player player) {
        String playerName = player.getName();

        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            if (player != null && p != null) {
                if (inSameParty(player, p) && !p.getName().equals(playerName)) {
                    p.sendMessage(mcLocale.getString("Party.InformedOnJoin", new Object[] {playerName}));
                }
            }
        }
    }

    /**
     * Get a list of all players in this player's party.
     *
     * @param player The player to check
     * @return all the players in the player's party
     */
    public ArrayList<Player> getPartyMembers(Player player) {
        ArrayList<Player> players = new ArrayList<Player>();

        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            if (p.isOnline() && player != null && p != null) {
                if (inSameParty(player, p) && !p.getName().equals(player.getName())) {
                    players.add(p);
                }
            }
        }
        return players;
    }

    /**
     * Notify party members when the party owner changes.
     *
     * @param newOwnerName The name of the new party owner
     */
    private void informPartyMembersOwnerChange(String newOwnerName) {
        Player newOwner = plugin.getServer().getPlayer(newOwnerName);

        for (Player p : Bukkit.getServer().getOnlinePlayers()){
            if (newOwner != null && p != null) {
                if (inSameParty(newOwner, p)) {
                    p.sendMessage(newOwnerName + " is the new party owner."); //TODO: Needs more locale
                }
            }
        }
    }

    /**
     * Notify party members when the a party member quits.
     *
     * @param player The player that quit
     */
    private void informPartyMembersQuit(Player player) {
        String playerName = player.getName();

        for (Player p : Bukkit.getServer().getOnlinePlayers()){
            if (player != null && p != null){
                if (inSameParty(player, p) && !p.getName().equals(playerName)) {
                    p.sendMessage(mcLocale.getString("Party.InformedOnQuit", new Object[] {playerName}));
                }
            }
        }
    }

    /**
     * Remove a player from a party.
     *
     * @param player The player to remove
     * @param PP The profile of the player to remove
     */
    public void removeFromParty(Player player, PlayerProfile PP) {
        String party = PP.getParty();
        String playerName = player.getName();

        //Stop NPE... hopefully
        if (!isParty(party) || !isInParty(player, PP)) {
            addToParty(player, PP, party, false, null);
        }

        informPartyMembersQuit(player);

        if (isPartyLeader(playerName, party)) {
            if (isPartyLocked(party)) {
                unlockParty(party);
            }
        }

        partyPlayers.get(party).remove(playerName);

        if (isPartyEmpty(party)) {
            deleteParty(party);
        }

        PP.removeParty();
        savePartyFile(partyPlayersFile, partyPlayers);
    }

    /**
     * Add a player to a party.
     *
     * @param player The player to add to the party
     * @param PP The profile of the player to add to the party
     * @param newParty The party to add the player to
     * @param invite true if the player was invited to this party, false otherwise
     * @param password the password for this party, null if there was no password
     */
    public void addToParty(Player player, PlayerProfile PP, String newParty, Boolean invite, String password) {
        String playerName = player.getName();

        //Fix for FFS
        newParty = newParty.replace(":", ".");

        //Don't care about passwords on invites
        if (!invite) {

            //Don't care about passwords if it isn't locked
            if (isPartyLocked(newParty)) {
                if (isPartyPasswordProtected(newParty)) {
                    if (password == null) {
                        player.sendMessage("This party requires a password. Use /party <party> <password> to join it."); //TODO: Needs more locale.
                        return;
                    }
                    else if(!password.equalsIgnoreCase(getPartyPassword(newParty))) {
                        player.sendMessage("Party password incorrect."); //TODO: Needs more locale.
                        return;
                    }
                }
                else {
                    player.sendMessage("Party is locked."); //TODO: Needs more locale.
                    return;
                }
            }
        }
        else {
            PP.acceptInvite();
        }

        //New party?
        if (!isParty(newParty)) {
            putNestedEntry(partyPlayers, newParty, playerName, true);

            //Get default locking behavior from config?
            partyLocks.put(newParty, false);
            partyPasswords.put(newParty, null);
            saveParties();
        }
        else {
            putNestedEntry(partyPlayers, newParty, playerName, false);
            savePartyFile(partyPlayersFile, partyPlayers);
        }

        PP.setParty(newParty);
        informPartyMembers(player);

        if (!invite) {
            player.sendMessage(mcLocale.getString("mcPlayerListener.JoinedParty", new Object[]{ newParty }));
        }
        else {
            player.sendMessage(mcLocale.getString("mcPlayerListener.InviteAccepted", new Object[]{ PP.getParty() }));
        }
    }

    private static <U,V,W> W putNestedEntry(HashMap<U, HashMap<V, W>> nest, U nestKey, V nestedKey, W nestedValue) {
        HashMap<V,W> nested = nest.get(nestKey);

        if (nested == null) {
            nested = new HashMap<V,W>();
            nest.put(nestKey, nested);
        }

        return nested.put(nestedKey, nestedValue);
    }

    /*
     * Any reason why we need to keep this function around?
     */
    private void dump(Player player) {
        player.sendMessage(partyPlayers.toString());
        player.sendMessage(partyLocks.toString());
        player.sendMessage(partyPasswords.toString());
        Iterator<String> i = partyPlayers.keySet().iterator();
        while(i.hasNext()) {
            String nestkey = i.next();
            player.sendMessage(nestkey);
            Iterator<String> j = partyPlayers.get(nestkey).keySet().iterator();
            while(j.hasNext()) {
                String nestedkey = j.next();
                player.sendMessage("."+nestedkey);
                if(partyPlayers.get(nestkey).get(nestedkey)) {
                    player.sendMessage("..True");
                } else {
                    player.sendMessage("..False");
                }
            }
        }
    }

    /**
     * Lock a party.
     *
     * @param partyName The party to lock
     */
    public void lockParty(String partyName) {
        partyLocks.put(partyName, true);
        savePartyFile(partyLocksFile, partyLocks);
    }

    /**
     * Unlock a party.
     *
     * @param partyName The party to unlock
     */
    public void unlockParty(String partyName) {
        partyLocks.put(partyName, false);
        savePartyFile(partyLocksFile, partyLocks);
    }

    /**
     * Delete a party.
     *
     * @param partyName The party to delete
     */
    private void deleteParty(String partyName) {
        partyPlayers.remove(partyName);
        partyLocks.remove(partyName);
        partyPasswords.remove(partyName);
        saveParties();
    }

    /**
     * Set the password for a party.
     *
     * @param partyName The party name
     * @param password The new party password
     */
    public void setPartyPassword(String partyName, String password) {
        if (password.equalsIgnoreCase("\"\"")) { //What's with that password string?
            password = null;
        }

        partyPasswords.put(partyName, password);
        savePartyFile(partyPasswordsFile, partyPasswords);
    }

    /**
     * Set the leader of a party.
     *
     * @param partyName The party name
     * @param playerName The name of the player to set as leader
     */
    public void setPartyLeader(String partyName, String playerName) {
        for (String name : partyPlayers.get(partyName).keySet()) {
            if (name.equalsIgnoreCase(playerName)) {
                partyPlayers.get(partyName).put(playerName, true);
                informPartyMembersOwnerChange(playerName);
                plugin.getServer().getPlayer(playerName).sendMessage("You are now the party owner."); //TODO: Needs more locale.
                continue;
            }

            if (partyPlayers.get(partyName).get(name)) {
                plugin.getServer().getPlayer(name).sendMessage("You are no longer party owner."); //TODO: Needs more locale.
                partyPlayers.get(partyName).put(name, false);
            }
        }
    }

    /**
     * Get the password of a party.
     *
     * @param partyName The party name
     * @return The password of this party
     */
    public String getPartyPassword(String partyName) {
        return partyPasswords.get(partyName);
    }

    /**
     * Check if a player can invite others to their party.
     *
     * @param player The player to check
     * @param PP The profile of the given player
     * @return true if the player can invite, false otherwise
     */
    public boolean canInvite(Player player, PlayerProfile PP) {
        String party = PP.getParty();

        if (isPartyLocked(party) && !isPartyLeader(player.getName(), party)) {
            return false;
        }
        else {
            return true;
        }
    }

    /**
     * Check if a string is a valid party name.
     *
     * @param partyName The party name to check
     * @return true if this is a valid party, false otherwise
     */
    public boolean isParty(String partyName) {
        return partyPlayers.containsKey(partyName);
    }

    /**
     * Check if a party is empty.
     *
     * @param partyName The party to check
     * @return true if this party is empty, false otherwise
     */
    public boolean isPartyEmpty(String partyName) {
        return partyPlayers.get(partyName).isEmpty();
    }

    /**
     * Check if a player is the party leader.
     *
     * @param playerName The player name to check
     * @param partyName The party name to check
     * @return true if the player is the party leader, false otherwise
     */
    public boolean isPartyLeader(String playerName, String partyName) {
        HashMap<String, Boolean> partyMembers = partyPlayers.get(partyName);

        if (partyMembers != null) {
            Boolean isLeader = partyMembers.get(playerName);

            if (isLeader == null) {
                return false;
            }
            else {
                return isLeader;
            }
        }
        else {
            return false;
        }
    }

    /**
     * Check if this party is locked.
     *
     * @param partyName The party to check
     * @return true if this party is locked, false otherwise
     */
    public boolean isPartyLocked(String partyName) {
        Boolean isLocked = partyLocks.get(partyName);

        if (isLocked ==  null) {
            return false;
        }
        else {
            return isLocked;
        }
    }

    /**
     * Check if this party is password protected.
     *
     * @param partyName The party to check
     * @return true if this party is password protected, false otherwise
     */
    public boolean isPartyPasswordProtected(String partyName) {
        String password = partyPasswords.get(partyName);

        if (password == null) {
            return false;
        }
        else {
            return true;
        }
    }

    /**
     * Check if a player is in the party reflected by their profile.
     *
     * @param player The player to check
     * @param PP The profile of the player
     * @return true if this player is in the right party, false otherwise
     */
    public boolean isInParty(Player player, PlayerProfile PP) {
        return partyPlayers.get(PP.getParty()).containsKey(player.getName());
    }

    /**
     * Load all party related files.
     */
    @SuppressWarnings("unchecked")
    public void loadParties() {
        if (new File(partyPlayersFile).exists()) {
            try {
                ObjectInputStream obj = new ObjectInputStream(new FileInputStream(partyPlayersFile));
                partyPlayers = (HashMap<String, HashMap<String, Boolean>>) obj.readObject();
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            catch (EOFException e) {
                Bukkit.getLogger().info("partyPlayersFile empty.");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (new File(partyLocksFile).exists()) {
            try {
                ObjectInputStream obj = new ObjectInputStream(new FileInputStream(partyLocksFile));
                partyLocks = (HashMap<String, Boolean>) obj.readObject();
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            catch (EOFException e) {
                Bukkit.getLogger().info("partyLocksFile empty.");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (new File(partyPasswordsFile).exists()) {
            try {
                ObjectInputStream obj = new ObjectInputStream(new FileInputStream(partyPasswordsFile));
                this.partyPasswords = (HashMap<String, String>) obj.readObject();
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            catch (EOFException e) {
                Bukkit.getLogger().info("partyPasswordsFile empty.");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Save all party-related files.
     */
    private void saveParties() {
        savePartyFile(partyPlayersFile, partyPlayers);
        savePartyFile(partyLocksFile, partyLocks);
        savePartyFile(partyPasswordsFile, partyPasswords);
    }

    /**
     * Save a party-related file.
     *
     * @param fileName The filename to save as
     * @param partyData The Hashmap with the party data
     */
    private void savePartyFile(String fileName, Object partyData) {
        try {
            new File(fileName).createNewFile();
            ObjectOutputStream obj = new ObjectOutputStream(new FileOutputStream(fileName));
            obj.writeObject(partyData);
            obj.close();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
