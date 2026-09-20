Feature: Remote multi-participant audio capture, upload, and clock synchronization
  A review conducted over a call, rather than in person around one shared
  microphone, still produces one coherent, correctly-ordered transcript
  (ticket #252). Each participant's own client captures only their own
  microphone and uploads that audio to the Review Recording they joined;
  the backend transcribes each participant's stream individually (single
  speaker per stream — no diarization needed here, unlike the in-person
  mode) and merges the results into one chronological conversation,
  ordered by when each segment was actually spoken rather than by upload
  order. Timestamps from different participants' own machines are not
  directly comparable, so joining a recording hands each participant a
  clock-anchoring basis to timestamp their own captured audio against
  before uploading it (ticket #251's `RemoteTranscriptMerger` already
  assumes its input streams' timestamps are comparable — this ticket is
  what makes that true for real, differently-clocked machines).

  Scenario: Joining a recording provides a basis for comparable timestamps
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Maria" joins that recording
    Then "Maria" receives a clock-anchoring basis for timestamping her own captured audio

  Scenario: Two participants' uploaded audio merges into one chronological transcript
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Maria" has joined that recording
    When "Petros" uploads audio in which he says "Could this execute twice?" before "Maria" speaks
    And "Maria" uploads audio in which she says "Only with a network retry" after "Petros" spoke
    Then the recording's transcript includes what each participant said
    And "Petros"'s words appear before "Maria"'s words in the transcript, matching when they were actually spoken

  Scenario: A participant's audio is transcribed as their own single speaker, without diarization
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    When "Petros" uploads audio in which he says "Let's add an idempotency key"
    Then the transcript segment is attributed to "Petros"

  Scenario: The recording still produces a usable transcript when a participant's audio never arrives
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Maria" has joined that recording
    When "Petros" uploads audio in which he says "Let's get started"
    And "Maria" never uploads her audio
    Then the recording's transcript includes what "Petros" said
    And the underlying Review Recording remains intact

  Scenario: Uploading audio against a recording that does not exist is rejected
    When a developer attempts to upload audio for recording "does-not-exist"
    Then the upload request is rejected as invalid

  Scenario: Uploading audio against a recording that has already stopped is rejected
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Petros" has stopped the recording
    When "Petros" attempts to upload audio to that recording
    Then the upload request is rejected as invalid

  Scenario: The merged remote transcript aligns to entities the same way an in-person transcript does
    Given "Petros" has started a Review Recording for PR 42 in "acme/widgets"
    And "Maria" has joined that recording
    And "Petros" inspects the "PaymentProcessor" entity
    And some time passes during the recording
    When "Maria" uploads audio in which she says "Could this execute twice?"
    And Athena aligns the recording's transcript to its semantic events
    Then the aligned segment references the "PaymentProcessor" entity
    And the aligned segment is attributed to speaker "Maria"
