package sn.arlettedebo.platform.communication.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.AuditableEntity;

/** Communication interne : l'information officielle est dans l'intranet (pas seulement WhatsApp). */
@Getter
@Entity
@Table(name = "td_note_interne")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteInterne extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private Type type;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "contenu", nullable = false)
    private String contenu;

    @Column(name = "auteur_id", nullable = false, updatable = false)
    private UUID auteurId;

    @Column(name = "publiee_le", nullable = false)
    private Instant publieeLe;

    @Column(name = "epinglee", nullable = false)
    private boolean epinglee;

    public NoteInterne(Type type, String titre, String contenu, UUID auteur, Instant le, boolean epinglee) {
        this.type = type;
        this.titre = titre;
        this.contenu = contenu;
        this.auteurId = auteur;
        this.publieeLe = le;
        this.epinglee = epinglee;
    }

    public enum Type { ACTUALITE, NOTE_SERVICE, ANNONCE, DOCUMENT }
}
