package ommp.archives.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;

class EmplacementOccupationServiceTest {

	@Test
	void fromBlocs_singleBloc_plageSansTiret() {
		Emplacement b = bloc("b1", "01112");
		var plage = EmplacementOccupationService.fromBlocs(List.of(b));
		assertEquals("b1", plage.debutId());
		assertEquals("b1", plage.finId());
		assertEquals("01112", plage.plage());
		assertEquals("01112", plage.numerosListe());
	}

	@Test
	void fromBlocs_multiBlocs_plageEtListe() {
		Emplacement b1 = bloc("id1", "01111");
		Emplacement b2 = bloc("id2", "01112");
		var plage = EmplacementOccupationService.fromBlocs(List.of(b1, b2));
		assertEquals("id1", plage.debutId());
		assertEquals("id2", plage.finId());
		assertEquals("01111-01112", plage.plage());
		assertEquals("01111, 01112", plage.numerosListe());
	}

	@Test
	void fromBlocs_empty() {
		var plage = EmplacementOccupationService.fromBlocs(List.of());
		assertNull(plage.debutId());
		assertNull(plage.plage());
	}

	@Test
	void formatRange_and_formatPlage() {
		assertEquals("01112", EmplacementPlageFormatter.formatRange("01112", "01112"));
		assertEquals("01111-01114", EmplacementPlageFormatter.formatRange("01111", "01114"));
		assertEquals("01112", EmplacementPlageFormatter.formatPlage("01112-01112"));
	}

	private static Emplacement bloc(String id, String numero) {
		Emplacement e = new Emplacement();
		e.setId(id);
		e.setNumero(numero);
		e.setTypeEmp(TypeEmp.BLOC);
		return e;
	}
}
