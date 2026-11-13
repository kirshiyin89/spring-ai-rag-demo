
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

const imageButton = document.getElementById("image-button");
const imageInput = document.getElementById("image-input");

const imagePreview = document.getElementById("image-preview");
const imagePreviewImg = document.getElementById("image-preview-img");
const imagePreviewName = document.getElementById("image-preview-name");

const removeImageButton =
    document.getElementById("remove-image-button");

let selectedImage = null;
let previewObjectUrl = null;

function clearImageSelection() {
  selectedImage = null;

  imageInput.value = "";
  imagePreview.classList.add("hidden");

  imagePreviewImg.removeAttribute("src");
  imagePreviewName.textContent = "";

  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl);
    previewObjectUrl = null;
  }
}

function handleImageSelection(file) {
  if (!file) {
    return;
  }

  const allowedTypes = ["image/png", "image/jpeg"];
  const maxSize = 5 * 1024 * 1024;

  if (!allowedTypes.includes(file.type)) {
    alert("Only PNG and JPEG images are supported.");
    imageInput.value = "";
    return;
  }

  if (file.size > maxSize) {
    alert("Image size must not exceed 5 MB.");
    imageInput.value = "";
    return;
  }

  clearImageSelection();

  selectedImage = file;

  previewObjectUrl = URL.createObjectURL(file);

  imagePreviewImg.src = previewObjectUrl;
  imagePreviewName.textContent = file.name;

  imagePreview.classList.remove("hidden");
}

imageButton.addEventListener("click", () => {
  if (!isProcessing) {
    imageInput.click();
  }
});

imageInput.addEventListener("change", () => {
  const file = imageInput.files[0];

  handleImageSelection(file);
});

removeImageButton.addEventListener("click", () => {
  clearImageSelection();
});

async function sendImageQuestion(question) {

  if (!selectedImage || isProcessing) {
    return;
  }

  const imageFile = selectedImage;

  const userMessage = addMessage(
      question.trim() || "Please analyze this error screenshot.",
      "user"
  );

  const messageImage = document.createElement("img");

  const messageImageUrl = URL.createObjectURL(imageFile);

  messageImage.src = messageImageUrl;
  messageImage.alt = "Uploaded error screenshot";
  messageImage.className = "chat-image-preview";

  messageImage.onload = () => {
    URL.revokeObjectURL(messageImageUrl);
  };

  userMessage.appendChild(messageImage);

  questionInput.value = "";

  clearImageSelection();

  setProcessing(true);

  const loadingMessage = addMessage(
      "Analyzing your screenshot...",
      "assistant"
  );

  try {
    const formData = new FormData();

    formData.append("image", imageFile);

    if (question.trim()) {
      formData.append("question", question);
    }

    const headers = await csrfHeaders();

    const response = await fetch("/api/images/analyze", {
      method: "POST",
      headers: headers,
      body: formData
    });

    if (!response.ok) {
      const errorMessage = await response.text();

      throw new Error(
          errorMessage || "Image analysis failed."
      );
    }

    const answer = await response.text();

    loadingMessage.textContent = answer;

  } catch (error) {
    console.error("Image analysis error:", error);

    loadingMessage.textContent =
        "Sorry, I couldn't analyze the screenshot. " +
        "Please try again.";

  } finally {
    setProcessing(false);
  }
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

// Handle typed questions and image uploads.
chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (selectedImage) {
    await sendImageQuestion(questionInput.value);
  } else {
    await sendTextQuestion(questionInput.value);
  }
});

function setProcessing(processing) {
  isProcessing = processing;

  sendButton.disabled = processing;
  micButton.disabled = processing;
  questionInput.disabled = processing;

  imageButton.disabled = processing;
  imageInput.disabled = processing;
  removeImageButton.disabled = processing;
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