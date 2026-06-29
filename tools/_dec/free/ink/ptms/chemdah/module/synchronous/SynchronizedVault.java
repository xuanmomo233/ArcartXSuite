/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ink.ptms.chemdah.taboolib.common5.Coerce
 *  kotlin.Metadata
 *  kotlin1822.collections.CollectionsKt
 *  kotlin1822.jvm.internal.Intrinsics
 *  net.milkbowl.vault.economy.AbstractEconomy
 *  net.milkbowl.vault.economy.EconomyResponse
 *  net.milkbowl.vault.economy.EconomyResponse$ResponseType
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package ink.ptms.chemdah.module.synchronous;

import ink.ptms.chemdah.api.ChemdahAPI;
import ink.ptms.chemdah.core.Data;
import ink.ptms.chemdah.core.DataContainer;
import ink.ptms.chemdah.module.synchronous.Synchronous;
import ink.ptms.chemdah.taboolib.common5.Coerce;
import ink.ptms.chemdah.util.UtilsForKetherKt;
import java.util.List;
import kotlin.Metadata;
import kotlin1822.collections.CollectionsKt;
import kotlin1822.jvm.internal.Intrinsics;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Metadata(mv={1, 8, 0}, k=1, xi=48, d1={"\u0000B\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0001\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\t\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u001a\u0010\u0007\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\u001a\u0010\n\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\u001a\u0010\u000b\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\u001c\u0010\f\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0016J\u0010\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u001a\u0010\r\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0016J\b\u0010\u000f\u001a\u00020\u0006H\u0016J\b\u0010\u0010\u001a\u00020\u0006H\u0016J\u0012\u0010\u0011\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u0018\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\"\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0014\u001a\u00020\tH\u0016J\u0010\u0010\u0015\u001a\u00020\u00062\u0006\u0010\u0016\u001a\u00020\tH\u0016J\b\u0010\u0017\u001a\u00020\u0018H\u0016J\u0010\u0010\u0019\u001a\u00020\t2\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u001a\u0010\u0019\u001a\u00020\t2\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0016J\u000e\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00060\u001bH\u0016J\b\u0010\u001c\u001a\u00020\u0006H\u0016J\u0018\u0010\u001d\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\"\u0010\u001d\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0014\u001a\u00020\tH\u0016J\u0010\u0010\u001e\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u0006H\u0016J\u001a\u0010\u001e\u001a\u00020\u000e2\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0016J\b\u0010\u001f\u001a\u00020\u000eH\u0016J\u001c\u0010 \u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0016J\u001c\u0010!\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0016J\b\u0010\"\u001a\u00020\u000eH\u0016J\u0018\u0010#\u001a\u00020\u00132\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0016J\"\u0010#\u001a\u00020\u00132\u0006\u0010\u0005\u001a\u00020\u00062\b\u0010\b\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u0014\u001a\u00020\tH\u0016\u00a8\u0006$"}, d2={"Link/ptms/chemdah/module/synchronous/SynchronizedVault;", "Lnet/milkbowl/vault/economy/AbstractEconomy;", "()V", "bankBalance", "", "name", "", "bankDeposit", "p1", "", "bankHas", "bankWithdraw", "createBank", "createPlayerAccount", "", "currencyNamePlural", "currencyNameSingular", "deleteBank", "depositPlayer", "Lnet/milkbowl/vault/economy/EconomyResponse;", "p2", "format", "p0", "fractionalDigits", "", "getBalance", "getBanks", "", "getName", "has", "hasAccount", "hasBankSupport", "isBankMember", "isBankOwner", "isEnabled", "withdrawPlayer", "Chemdah"})
public final class SynchronizedVault
extends AbstractEconomy {
    public boolean isEnabled() {
        return true;
    }

    @NotNull
    public String getName() {
        return "chemdah";
    }

    public boolean hasBankSupport() {
        return false;
    }

    public int fractionalDigits() {
        return -1;
    }

    @NotNull
    public String format(double p0) {
        return String.valueOf(Coerce.format((double)p0));
    }

    @NotNull
    public String currencyNamePlural() {
        return "";
    }

    @NotNull
    public String currencyNameSingular() {
        return "";
    }

    public boolean hasAccount(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return true;
    }

    public boolean hasAccount(@NotNull String name, @Nullable String p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return true;
    }

    public double getBalance(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Player player2 = Bukkit.getPlayerExact((String)name);
        if (player2 == null) {
            throw new IllegalStateException("player offline".toString());
        }
        Player player3 = player2;
        DataContainer dataContainer = ChemdahAPI.INSTANCE.getChemdahProfile(player3).getPersistentDataContainer();
        String string = Synchronous.INSTANCE.getPlayerDataToVault();
        Intrinsics.checkNotNull((Object)string);
        Object object = dataContainer.get(string);
        if (object == null || (object = ((Data)object).getData()) == null) {
            object = 0;
        }
        return Coerce.toDouble((Object)object);
    }

    public double getBalance(@NotNull String name, @Nullable String p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return this.getBalance(name);
    }

    public boolean has(@NotNull String name, double p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return this.getBalance(name) >= p1;
    }

    public boolean has(@NotNull String name, @Nullable String p1, double p2) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return this.getBalance(name) >= p2;
    }

    @NotNull
    public EconomyResponse withdrawPlayer(@NotNull String name, double p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return this.depositPlayer(name, -p1);
    }

    @NotNull
    public EconomyResponse withdrawPlayer(@NotNull String name, @Nullable String p1, double p2) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return this.depositPlayer(name, -p2);
    }

    @NotNull
    public EconomyResponse depositPlayer(@NotNull String name, double p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        Player player2 = Bukkit.getPlayerExact((String)name);
        if (player2 == null) {
            throw new IllegalStateException("player offline".toString());
        }
        Player player3 = player2;
        DataContainer dataContainer = ChemdahAPI.INSTANCE.getChemdahProfile(player3).getPersistentDataContainer();
        String string = Synchronous.INSTANCE.getPlayerDataToVault();
        Intrinsics.checkNotNull((Object)string);
        String string2 = Synchronous.INSTANCE.getPlayerDataToVault();
        Intrinsics.checkNotNull((Object)string2);
        dataContainer.set(string, UtilsForKetherKt.increaseAny(dataContainer.get(string2), p1));
        return new EconomyResponse(p1, 0.0, EconomyResponse.ResponseType.SUCCESS, "");
    }

    @NotNull
    public EconomyResponse depositPlayer(@NotNull String name, @Nullable String p1, double p2) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return this.depositPlayer(name, p2);
    }

    @Nullable
    public Void createBank(@NotNull String name, @Nullable String p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @Nullable
    public Void deleteBank(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @Nullable
    public Void bankBalance(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @Nullable
    public Void bankHas(@NotNull String name, double p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @Nullable
    public Void bankWithdraw(@NotNull String name, double p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @Nullable
    public Void bankDeposit(@NotNull String name, double p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @Nullable
    public Void isBankOwner(@NotNull String name, @Nullable String p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @Nullable
    public Void isBankMember(@NotNull String name, @Nullable String p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return null;
    }

    @NotNull
    public List<String> getBanks() {
        return CollectionsKt.emptyList();
    }

    public boolean createPlayerAccount(@NotNull String name) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return true;
    }

    public boolean createPlayerAccount(@NotNull String name, @Nullable String p1) {
        Intrinsics.checkNotNullParameter((Object)name, (String)"name");
        return true;
    }
}

