
const conversationId = crypto.randomUUID();

const chatMessages = document.getElementById("chat-messages");
const chatForm = document.getElementById("chat-form");
const questionInput = document.getElementById("question-input");
const sendButton = document.getElementById("send-button");
const micButton = document.getElementById("mic-button");
const recordingStatus = document.getElementById("recording-status");

let mediaRecorder = null;
let audioChunks = [];
let isRecording = false;
let isProcessing = false;

async function getCsrfToken() {
  const response = await fetch("/api/chat/csrf");

  if (!response.ok) {
    throw new Error("Could not retrieve CSRF token");
  }

  return await response.json();
}

async function csrfHeaders() {
  const csrf = await getCsrfToken();

  return {
    [csrf.headerName]: csrf.token
  };
}

// Display a message in the chat window.
function addMessage(text, sender) {
  const message = document.createElement("div");

  message.classList.add("message", sender);
  message.textContent = text;

  chatMessages.appendChild(message);
  chatMessages.scrollTop = chatMessages.scrollHeight;

  return message;
}

// Send a typed question to the backend.
async function sendTextQuestion(question) {
  if (!question.trim() || isProcessing) {
    return;
  }

  addMessage(question, "user");

  questionInput.value = "";
  setProcessing(true);

  const loadingMessage = addMessage("Thinking...", "assistant");

  try {
    const params = new URLSearchParams({
      conversationId: conversationId,
      question: question
    });

    const response = await fetch(
        `/api/chat/rag?${params.toString()}`
    );

    if (!response.ok) {
      throw new Error("Chat request failed.");
    }

    const answer = await response.text();

    loadingMessage.textContent = answer;

  } catch (error) {
    console.error(error);
    loadingMessage.textContent =
        "Sorry, something went wrong. Please try again.";
  } finally {
    setProcessing(false);
  }
}

// Send recorded audio to the backend.

async function sendVoiceQuestion(audioBlob) {
  setProcessing(true);

  try {
    const formData = new FormData();

    formData.append("audio", audioBlob, "recording.webm");

    const headers = await csrfHeaders();

    const response = await fetch("/api/voice/transcribe", {
      method: "POST",
      headers,
      body: formData
    });

    if (!response.ok) {
      throw new Error("Transcription failed.");
    }

    const question = await response.text();

    // Put the transcription into the existing input.
    questionInput.value = question;

    // Let the user review or correct the transcription.
    questionInput.focus();

  } catch (error) {
    console.error(error);

    addMessage(
        "Sorry, I couldn't transcribe your recording.",
        "assistant"
    );

  } finally {
    setProcessing(false);
  }
}


// Start microphone recording.
async function startRecording() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: true
    });

    audioChunks = [];

    const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : "audio/webm";

    mediaRecorder = new MediaRecorder(stream, {
      mimeType: mimeType
    });

    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.push(event.data);
      }
    };

    mediaRecorder.onstop = async () => {
      const audioBlob = new Blob(audioChunks, {
        type: mediaRecorder.mimeType
      });

      stream.getTracks().forEach(track => track.stop());

      await sendVoiceQuestion(audioBlob);
    };

    mediaRecorder.start();

    isRecording = true;

    micButton.classList.add("recording");
    micButton.textContent = "⏹";
    recordingStatus.classList.remove("hidden");

  } catch (error) {
    console.error(error);

    addMessage(
        "Could not access your microphone. Please check browser permissions.",
        "assistant"
    );
  }
}

// Stop microphone recording.
function stopRecording() {
  if (mediaRecorder && isRecording) {
    mediaRecorder.stop();

    isRecording = false;

    micButton.classList.remove("recording");
    micButton.textContent = "🎤";
    recordingStatus.classList.add("hidden");
  }
}

// Handle microphone button.
micButton.addEventListener("click", async () => {
  if (isProcessing) {
    return;
  }

  if (isRecording) {
    stopRecording();
  } else {
    await startRecording();
  }
});

// Handle typed questions.
chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  await sendTextQuestion(questionInput.value);
});

// Disable controls while processing.
function setProcessing(processing) {
  isProcessing = processing;

  sendButton.disabled = processing;
  micButton.disabled = processing;
  questionInput.disabled = processing;
}

document.addEventListener("DOMContentLoaded", async () => {
  const csrfInput = document.getElementById("csrfToken");

  if (!csrfInput) return;

  try {
    const csrf = await getCsrfToken();
    csrfInput.value = csrf.token;
  } catch (error) {
    console.error("Failed to initialize CSRF token:", error);
  }
});