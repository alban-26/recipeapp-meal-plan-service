package com.myapp.adapter.restapi;

import com.my.common.api.UserId;
import com.myapp.mealplan.adapter.MealPlanConverter;
import com.myapp.mealplan.application.MealPlanAccess;
import com.myapp.mealplan.domain.model.MealPlan;
import com.myapp.mealplan.domain.model.MealPlanId;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openapitools.model.MealPlanDto;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;

@QuarkusTest
class MealPlanResourceTest {

    @InjectMock
    MealPlanAccess mealPlanAccess;

    @InjectMock
    MealPlanConverter mealPlanConverter;

    @InjectMock
    JsonWebToken jwt;

    // --- GET /mealplans ---
    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void testGetMealPlans_returnsList() {
        Mockito.when(jwt.getSubject()).thenReturn("user1");

        MealPlan mealPlan = new MealPlan(
                new MealPlanId(1L),
                LocalDate.of(2000, 1, 1),
                LocalDate.of(2000, 1, 2),
                List.of(),
                new UserId("user1")
        );

        MealPlanDto dto = new MealPlanDto();
        dto.setId(1L);
        dto.setStartDate(LocalDate.of(2000, 1, 1));
        dto.setEndDate(LocalDate.of(2000, 1, 2));

        // Mock service and converter
        Mockito.when(mealPlanAccess.getAllByUser(new UserId("user1"))).thenReturn(List.of(mealPlan));
        Mockito.when(mealPlanConverter.domainToDto(mealPlan)).thenReturn(dto);

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/mealPlans")
                .then()
                .statusCode(200)
                .body("[0].id", equalTo(1))
                .body("[0].startDate", equalTo("2000-01-01"))
                .body("[0].endDate", equalTo("2000-01-02"));
    }

    // --- POST /mealplans ---
    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void testCreateMealPlan_createsSuccessfully() {
        Mockito.when(jwt.getSubject()).thenReturn("user1");


        MealPlanDto inputDto = new MealPlanDto();
        inputDto.setStartDate(LocalDate.of(2000, 1, 1));
        inputDto.setEndDate(LocalDate.of(2000, 1, 2));


        MealPlan domainPlan = new MealPlan(
                new MealPlanId(1L),
                LocalDate.of(2000, 1, 1),
                LocalDate.of(2000, 1, 2),
                List.of(),
                new UserId("user1")
        );
        MealPlan savedPlan = new MealPlan(
                new MealPlanId(1L),
                LocalDate.of(2000, 1, 1),
                LocalDate.of(2000, 1, 2),
                List.of(),
                new UserId("user1")
        );

        MealPlanDto savedDto = new MealPlanDto();
        savedDto.setId(1L);
        savedDto.setStartDate(LocalDate.of(2000, 1, 1));
        savedDto.setEndDate(LocalDate.of(2000, 1, 2));

        Mockito.when(mealPlanConverter.dtoToDomain(any(MealPlanDto.class))).thenReturn(domainPlan);
        Mockito.when(mealPlanConverter.domainToDto(savedPlan)).thenReturn(savedDto);
        Mockito.when(mealPlanAccess.save(any(MealPlan.class))).thenReturn(savedPlan);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(inputDto)
                .queryParam("generateShoppingList", false)
                .when()
                .post("/mealPlans")
                .then()
                .statusCode(201)
                .header("Location", org.hamcrest.Matchers.endsWith("/mealplans/1"))
                .body("id", equalTo(1))
                .body("startDate", equalTo("2000-01-01"))
                .body("endDate", equalTo("2000-01-02"));
    }

    // --- GET /mealplans/{id} ---
    @Test
    @TestSecurity(user = "user1", roles = {"user"})
    void testGetMealPlanById_returnsMealPlan() {
        Mockito.when(jwt.getSubject()).thenReturn("user1");

        // Domain object
        MealPlan mealPlan = new MealPlan(
                new MealPlanId(1L),
                LocalDate.of(2000, 1, 1),
                LocalDate.of(2000, 1, 2),
                List.of(),
                new UserId("user1")
        );

        MealPlanDto dto = new MealPlanDto();
        dto.setId(1L);
        dto.setStartDate(LocalDate.of(2000, 1, 1));
        dto.setEndDate(LocalDate.of(2000, 1, 2));

        Mockito.when(mealPlanAccess.getById(new MealPlanId(1L))).thenReturn(Optional.of(mealPlan));
        Mockito.when(mealPlanConverter.domainToDto(mealPlan)).thenReturn(dto);

        given()
                .accept(ContentType.JSON)
                .when()
                .get("/mealPlans/1")
                .then()
                .statusCode(200)
                .body("id", equalTo(1))
                .body("startDate", equalTo("2000-01-01"))
                .body("endDate", equalTo("2000-01-02"));
    }
}
