package ommp.archives.entity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.PreRemove;

import ommp.archives.service.EmplacementOccupationService;

@Component
public class BoiteEntityListener {

	private static EmplacementOccupationService emplacementOccupationService;

	@Autowired
	void setEmplacementOccupationService(EmplacementOccupationService service) {
		BoiteEntityListener.emplacementOccupationService = service;
	}

	@PreRemove
	void onPreRemove(Boite boite) {
		if (emplacementOccupationService != null && boite.getId() != null) {
			emplacementOccupationService.releaseForBoite(boite.getId());
		}
	}
}
