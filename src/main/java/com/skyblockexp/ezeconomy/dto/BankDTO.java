package com.skyblockexp.ezeconomy.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Data Transfer Object for bank data.
 */
public class BankDTO {
    private final String name;
    private final UUID owner;
    private final Set<UUID> members;

    public BankDTO(String name, UUID owner, Set<UUID> members) {
        this.name = name;
        this.owner = owner;
        this.members = members;
    }

    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
}
