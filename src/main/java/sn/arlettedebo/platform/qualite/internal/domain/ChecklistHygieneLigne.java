package sn.arlettedebo.platform.qualite.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.domain.BaseEntity;

@Getter
@Entity
@Table(name = "td_checklist_hygiene_ligne")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistHygieneLigne extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checklist_id", nullable = false, updatable = false)
    private ChecklistHygiene checklist;

    @Column(name = "item_code", nullable = false, updatable = false)
    private String itemCode;

    @Column(name = "conforme", nullable = false)
    private boolean conforme;

    @Column(name = "commentaire")
    private String commentaire;

    ChecklistHygieneLigne(ChecklistHygiene checklist, String itemCode, boolean conforme, String commentaire) {
        this.checklist = checklist;
        this.itemCode = itemCode;
        this.conforme = conforme;
        this.commentaire = commentaire;
    }
}
