package de.mangalib.watchdogmodel;

import java.util.List;
import java.util.Objects;

import static de.mangalib.watchdogmodel.BinaryIpcCodec.*;
import static de.mangalib.watchdogmodel.WatchdogModel.*;

/**
 * Schmale Zuordnung von Wire-IDs zu VORHER registrierten Modell-Capabilities.
 * Der lokale Sender stammt vom Endpunkt, niemals aus dem Frame. Kein Peer-Auth-
 * Nachweis für echte Windows-Prozesse, kein zweiter Admission-/Recoveryautomat.
 */
final class WatchdogWireBridge implements Sink {
    private final WatchdogModel model;
    private final Ticket ticket;
    private final Channel channel;
    private final Object localSender;
    private final Id sessionId, ticketId, channelId, senderId, groupId;
    private final WireIdentityRegistry registry;
    private final Sink content;

    WatchdogWireBridge(WatchdogModel model, Direction direction, Object localSender,
                       Id session, Id ticket, Id channel, Id sender, Id group, WireIdentityRegistry registry, Sink content) {
        this.model = Objects.requireNonNull(model); this.ticket = Objects.requireNonNull(model.ticket());
        this.channel = Objects.requireNonNull(model.channel(direction)); this.localSender = Objects.requireNonNull(localSender);
        sessionId = Objects.requireNonNull(session); ticketId = Objects.requireNonNull(ticket);
        channelId = Objects.requireNonNull(channel); senderId = Objects.requireNonNull(sender); groupId = Objects.requireNonNull(group);
        this.registry = Objects.requireNonNull(registry);
        this.content = Objects.requireNonNull(content);
        registry.bind(WireIdentityRegistry.Domain.SESSION, sessionId, model.supervisor());
        registry.bind(WireIdentityRegistry.Domain.TICKET, ticketId, this.ticket);
        registry.bind(WireIdentityRegistry.Domain.CHANNEL, channelId, this.channel);
        registry.bind(WireIdentityRegistry.Domain.SENDER, senderId, this.channel.principal);
        model.requireChannelEndProofs(this.ticket);
    }

    Frame outbound(long sequence, Body body, String text) {
        Payload payload;
        if (body instanceof Signal signal) payload = new Control(signal.type(), text);
        else if (body instanceof ResultBegin begin) payload = new Begin(begin.bytes(), begin.fields(), begin.depth());
        else if (body instanceof ResultChunk chunk) {
            if (utf8Length(text) != chunk.bytes()) throw new IllegalArgumentException("chunk-content-length");
            payload = new Data(text);
        } else if (body instanceof GroupEnd end) {
            FakeJobKernel.Observation evidence = end.evidence();
            if (model.inner() == null || evidence.job() != model.inner().job || !evidence.members().isEmpty())
                throw new IllegalArgumentException("group-end");
            registry.bind(WireIdentityRegistry.Domain.GROUP, groupId, model.inner().job);
            payload = new GroupProof(groupId, evidence.revision(), evidence.complete(), evidence.stable(), evidence.active(), evidence.accounting());
        } else throw new IllegalArgumentException("body");
        return new Frame(new Envelope(sessionId, ticketId, channelId, senderId, channel.direction, sequence), payload);
    }

    @Override public boolean accept(Frame frame, long wireBytes) {
        Envelope envelope = frame.envelope();
        Body body;
        if (frame.payload() instanceof Control c) body = new Signal(c.type());
        else if (frame.payload() instanceof Begin b) body = new ResultBegin(b.bytes(), b.fields(), b.depth());
        else if (frame.payload() instanceof DataCount d) body = new ResultChunk(d.bytes());
        else if (frame.payload() instanceof GroupProof g) {
            if (model.inner() != null) registry.bind(WireIdentityRegistry.Domain.GROUP, groupId, model.inner().job);
            FakeJobKernel.Job job = groupId.equals(g.group()) && model.inner() != null ? model.inner().job : null;
            body = new GroupEnd(new FakeJobKernel.Observation(job, g.revision(), g.complete(), g.stable(),
                    g.active(), List.of(), g.accounting()));
        } else return false;
        Ticket mapped = ticketId.equals(envelope.ticket()) ? ticket : null;
        Channel mappedChannel = sessionId.equals(envelope.session()) && channelId.equals(envelope.channel())
                && envelope.direction() == channel.direction ? channel : null;
        Object mappedSender = senderId.equals(envelope.sender()) ? localSender : null;
        boolean accepted = model.accept(mappedChannel, mappedSender, new Message(mapped, VERSION, envelope.sequence(), wireBytes, wireBytes, body));
        return accepted && (!(frame.payload() instanceof DataCount) || content.accept(frame, wireBytes));
    }

    @Override public boolean dataByte(Envelope envelope, int value) { return content.dataByte(envelope, value); }
    @Override public void discard(Envelope envelope) { content.discard(envelope); }

    void transportEnded(BinaryIpcCodec.Error reason) { model.eof(channel, reason == null); }
}
