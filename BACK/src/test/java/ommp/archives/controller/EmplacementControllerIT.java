package ommp.archives.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;
import ommp.archives.repository.EmplacementRepository;

@SpringBootTest
@AutoConfigureMockMvc
class EmplacementControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EmplacementRepository emplacementRepository;

	@BeforeEach
	void cleanup() {
		emplacementRepository.deleteAll();
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void listEpis_empty_returnsOk() throws Exception {
		mockMvc.perform(get("/api/emplacements/epis"))
			.andExpect(status().isOk())
			.andExpect(content().json("[]"));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void listEpis_one_returnsSummary() throws Exception {
		Emplacement e = new Emplacement();
		e.setId(UUID.randomUUID().toString());
		e.setTypeEmp(TypeEmp.EPI);
		e.setNumero("E-TEST");
		e.setMetrage(0.0);
		emplacementRepository.save(e);

		mockMvc.perform(get("/api/emplacements/epis"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].numero").value("E-TEST"))
			.andExpect(jsonPath("$[0].traversCount").value(0));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void nextNumero_empty_returns01() throws Exception {
		mockMvc.perform(get("/api/emplacements/epis/next-numero"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.numero").value("01"));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void nextNumero_afterCreate_returns02() throws Exception {
		String body = """
			{"typeLabel":null,"traversCount":2,"tabletteRows":2,"blocsPerTablette":2,"blocLinearCm":10}
			""";
		mockMvc.perform(
			post("/api/emplacements/epis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.numero").value("01"));

		mockMvc.perform(get("/api/emplacements/epis/next-numero"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.numero").value("02"));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void matrix_hierarchicalNumeros() throws Exception {
		String body = """
			{"typeLabel":null,"traversCount":2,"tabletteRows":2,"blocsPerTablette":2,"blocLinearCm":10}
			""";
		mockMvc.perform(
			post("/api/emplacements/epis")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.numero").value("01"));

		String id = emplacementRepository.findEpisRootsOrderByCreatedAtAsc().get(0).getId();
		mockMvc.perform(get("/api/emplacements/epis/" + id + "/matrix"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.traversHeaders[0].numero").value("011"))
			.andExpect(jsonPath("$.traversHeaders[1].numero").value("012"))
			.andExpect(jsonPath("$.rows[0][0].tabletteNumero").value("0111"))
			.andExpect(jsonPath("$.rows[0][1].tabletteNumero").value("0121"))
			.andExpect(jsonPath("$.rows[1][0].tabletteNumero").value("0112"))
			.andExpect(jsonPath("$.rows[1][1].tabletteNumero").value("0122"))
			.andExpect(jsonPath("$.rows[0][0].blocs[0].numero").value("01111"))
			.andExpect(jsonPath("$.rows[0][0].blocs[1].numero").value("01112"))
			.andExpect(jsonPath("$.rows[0][1].blocs[0].numero").value("01211"));
	}
}
