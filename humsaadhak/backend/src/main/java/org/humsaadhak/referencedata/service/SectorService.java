package org.humsaadhak.referencedata.service;

import java.util.List;
import org.humsaadhak.referencedata.dao.SectorDao;
import org.humsaadhak.referencedata.dto.SectorCreateBody;
import org.humsaadhak.referencedata.dto.SectorRow;
import org.humsaadhak.referencedata.dto.SectorUpdateBody;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.util.Codes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Super admins add sectors, rename unused ones, and switch sectors off. Purposes and needs store the sector name, so a
 * sector already in use is never renamed or deleted (that would orphan records); it can be switched off instead, which
 * hides it from new forms while existing records keep it.
 */
@Service
@Transactional
public class SectorService {
    private final SectorDao sectors;

    public SectorService(SectorDao sectors) { this.sectors = sectors; }

    @Transactional(readOnly = true)
    public List<SectorRow> list() { return sectors.findAll(); }

    public SectorRow create(SectorCreateBody b) {
        String name = b.name().trim();
        String code = Codes.fromName(name);
        if (sectors.existsCodeOrName(code, name)) throw new DomainException("duplicate", "A sector called “" + name + "” already exists.");
        sectors.insert(code, name);
        return get(code);
    }

    public SectorRow update(String code, SectorUpdateBody b) {
        SectorRow current = get(code);
        String name = b.name().trim();
        if (!name.equals(current.name())) {
            if (current.usedBy() > 0)
                throw new DomainException("in-use", "“" + current.name() + "” is used by " + current.usedBy()
                        + " purposes, causes or projects, so it can’t be renamed. Add a new sector and switch this one off instead.");
            if (sectors.nameTakenByAnother(name, code)) throw new DomainException("duplicate", "A sector called “" + name + "” already exists.");
        }
        sectors.update(code, name, b.active());
        return get(code);
    }

    private SectorRow get(String code) { return sectors.find(code).orElseThrow(() -> new NotFoundException("Sector", code)); }
}
