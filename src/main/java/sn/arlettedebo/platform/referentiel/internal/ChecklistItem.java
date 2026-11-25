package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tp_checklist_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem extends Referentiel {

    @Column(name = "moment", nullable = false)
    private String moment;
    
    @Column(name = "ordre", nullable = false)
    private short ordre;
    
    @Column(name = "actif", nullable = false)
    private boolean actif;
}
