package com.athena.cucumber;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectSmokeSteps {

    private boolean pipelineWiredUp;

    @Given("the Athena test pipeline is wired up")
    public void the_pipeline_is_wired_up() {
        pipelineWiredUp = true;
    }

    @Then("the pipeline reports success")
    public void the_pipeline_reports_success() {
        assertThat(pipelineWiredUp).isTrue();
    }
}
