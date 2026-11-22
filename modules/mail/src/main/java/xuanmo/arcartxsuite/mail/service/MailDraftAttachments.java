package xuanmo.arcartxsuite.mail.service;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import xuanmo.arcartxsuite.api.inventory.PlayerDataReceipt;

/** Stores draft items and links the player's unfinished send to its durable receipt. */
final class MailDraftAttachments {
    static final NamespacedKey RECEIPT = new NamespacedKey("arcartxsuite", "mail_draft_attachments");
    static final NamespacedKey RETURN_STARTED = new NamespacedKey("arcartxsuite", "mail_attachment_return_started");
    private final Path directory;
    private final Path playerData;
    private final Gson gson = new Gson();

    // Only the side whose token is stored in playerdata owns the listed items.
    private record Journal(String before, List<String> beforeItems, String after, List<String> afterItems,
                           Handoff send, boolean returning, boolean fencedReturn) { }
    private record Snapshot(String receipt, List<String> items) { }

    record Handoff(String key, UUID recipient, boolean fundsOnly) { }
    record Recovery(List<String> items, Handoff send) { }
    record Change(UUID owner, String receipt, boolean empty) { }

    MailDraftAttachments(Path directory, Path playerData) {
        this.directory = directory;
        this.playerData = playerData;
    }

    Change prepare(UUID uuid, String live, List<String> items, boolean returning) throws IOException {
        Snapshot before = confirmed(uuid, live, load(uuid));
        String receipt = UUID.randomUUID().toString();
        write(uuid, new Journal(before.receipt(), before.items(), receipt, List.copyOf(items), null, returning, returning));
        return new Change(uuid, receipt, items.isEmpty());
    }

    void confirm(Change change) throws IOException {
        if (!change.receipt().equals(saved(change.owner()))) throw new IOException("附件背包存档未确认: " + change.receipt());
        if (change.empty()) Files.deleteIfExists(file(change.owner()));
    }

    /** Only used after the server thread has decided that the inventory mutation never ran. */
    void abandon(Change change) throws IOException {
        UUID uuid = change.owner();
        Journal journal = load(uuid);
        if (journal == null || !change.receipt().equals(journal.after())) throw new IOException("附件凭证已变化");
        if (journal.beforeItems().isEmpty()) Files.deleteIfExists(file(uuid));
        else write(uuid, new Journal(journal.before(), journal.beforeItems(), journal.before(), journal.beforeItems(),
            null, false, false));
    }

    Recovery inspect(UUID uuid, String live) throws IOException {
        Journal journal = load(uuid);
        if (journal == null) return new Recovery(List.of(), null);
        if (journal.send() != null) return new Recovery(List.of(), journal.send());
        Snapshot snapshot = confirmed(uuid, live, journal);
        if (snapshot.items().isEmpty()) Files.deleteIfExists(file(uuid));
        return new Recovery(snapshot.items(), null);
    }

    void handoff(UUID uuid, String live, Handoff send) throws IOException {
        Journal journal = load(uuid);
        if (send.fundsOnly()) {
            if (journal != null) throw new IOException("已有未完成的寄信或附件凭证");
            write(uuid, new Journal("", List.of(), "", List.of(), send, false, false));
            return;
        }
        Snapshot snapshot = confirmed(uuid, live, journal);
        if (snapshot.items().isEmpty()) throw new IOException("找不到寄信附件凭证");
        write(uuid, new Journal(snapshot.receipt(), snapshot.items(), snapshot.receipt(), snapshot.items(), send, false, false));
    }

    void finishHandoff(UUID uuid, String sendKey) throws IOException {
        Journal journal = load(uuid);
        if (journal == null) return;
        if (journal.send() != null && sendKey.equals(journal.send().key())) Files.deleteIfExists(file(uuid));
        else throw new IOException("附件凭证归属已变化: " + sendKey);
    }

    List<String> unsentItems(UUID uuid, String live, Handoff send) throws IOException {
        Journal journal = load(uuid);
        if (journal == null || !send.equals(journal.send())) throw new IOException("附件凭证归属已变化: " + send.key());
        return matchedSnapshot(uuid, live, journal).items();
    }

    private Snapshot confirmed(UUID uuid, String live, Journal journal) throws IOException {
        if (journal != null && journal.send() != null) throw new IOException("附件已转入寄信凭证: " + journal.send().key());
        return matchedSnapshot(uuid, live, journal);
    }

    private Snapshot matchedSnapshot(UUID uuid, String live, Journal journal) throws IOException {
        String receipt = saved(uuid);
        if (!Objects.equals(receipt, live)) throw new IOException("附件的在线回执和存档回执不一致");
        if (journal == null) return new Snapshot(receipt, List.of());
        if (Objects.equals(journal.after(), receipt)) return new Snapshot(receipt, journal.afterItems());
        if (Objects.equals(journal.before(), receipt) && (!journal.returning()
                || journal.fencedReturn() && !returnStarted(uuid, journal.after()))) {
            return new Snapshot(receipt, journal.beforeItems());
        }
        throw new IOException("附件凭证与背包存档不匹配");
    }

    boolean returnStarted(UUID uuid, String token) throws IOException {
        return token.equals(PlayerDataReceipt.read(playerData.resolve(uuid + ".dat"), RETURN_STARTED.toString()));
    }

    private String saved(UUID uuid) throws IOException {
        return PlayerDataReceipt.read(playerData.resolve(uuid + ".dat"), RECEIPT.toString());
    }

    private Journal load(UUID uuid) throws IOException {
        Path path = file(uuid);
        if (Files.notExists(path)) return null;
        Journal journal = gson.fromJson(Files.readString(path), Journal.class);
        if (journal == null || journal.after() == null || journal.beforeItems() == null || journal.afterItems() == null) {
            throw new IOException("附件凭证内容不完整");
        }
        if (journal.send() != null && (journal.send().key() == null || !journal.send().key().matches("[a-f0-9]{64}")
                || journal.send().recipient() == null)) throw new IOException("寄信交接凭证内容不完整");
        return journal;
    }

    private void write(UUID uuid, Journal journal) throws IOException {
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "attachments-", ".tmp");
        try {
            Files.writeString(temp, gson.toJson(journal));
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temp, file(uuid), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path file(UUID uuid) {
        return directory.resolve(uuid + ".json");
    }
}
