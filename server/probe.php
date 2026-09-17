<?php

$KEY = '';
$DIR = __DIR__ . '/probe-data';
$MAX_BYTES = 262144;
$KEEP_DAYS = 30;
$MAX_FILES = 200;
$RATE_PER_HOUR = 12;

header('Content-Type: text/plain; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

function fail($code, $message) {
    header('HTTP/1.1 ' . $code);
    echo $message . "\n";
    exit;
}

function app_slug($raw) {
    $slug = strtolower(trim((string) $raw));
    return preg_match('/^[a-z0-9][a-z0-9-]{0,23}$/', $slug) ? $slug : 'altro';
}

function sweep($dir, $keepDays, $maxFiles) {
    $files = glob($dir . '/*.txt');
    if (!$files) {
        return;
    }
    rsort($files);
    $cutoff = time() - ($keepDays * 86400);
    $kept = 0;
    foreach ($files as $file) {
        $kept++;
        if ($kept > $maxFiles || filemtime($file) < $cutoff) {
            @unlink($file);
        }
    }
}

function within_rate($dir, $limit) {
    $file = $dir . '/.rate';
    $address = isset($_SERVER['REMOTE_ADDR']) ? $_SERVER['REMOTE_ADDR'] : '?';
    $who = substr(hash('sha256', $address), 0, 16);
    $now = time();
    $state = array();
    if (is_file($file)) {
        $raw = @json_decode((string) @file_get_contents($file), true);
        if (is_array($raw)) {
            $state = $raw;
        }
    }
    foreach ($state as $key => $times) {
        $recent = array();
        foreach ((array) $times as $moment) {
            if ($moment > $now - 3600) {
                $recent[] = $moment;
            }
        }
        if ($recent) {
            $state[$key] = $recent;
        } else {
            unset($state[$key]);
        }
    }
    $mine = isset($state[$who]) ? $state[$who] : array();
    if (count($mine) >= $limit) {
        return false;
    }
    $mine[] = $now;
    $state[$who] = $mine;
    @file_put_contents($file, json_encode($state), LOCK_EX);
    @chmod($file, 0600);
    return true;
}

$given = isset($_POST['k']) ? (string) $_POST['k'] : '';
if ($KEY === '' || $given === '' || !hash_equals($KEY, $given)) {
    fail('403 Forbidden', '403');
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST' || !isset($_POST['text'])) {
    fail('405 Method Not Allowed', 'ERR Serve un POST con text.');
}

$text = (string) $_POST['text'];
$note = isset($_POST['note']) ? trim((string) $_POST['note']) : '';
$app = app_slug(isset($_POST['app']) ? $_POST['app'] : '');

if (trim($text) === '') {
    fail('400 Bad Request', 'ERR Il referto era vuoto.');
}
if (strlen($text) > $MAX_BYTES) {
    fail('400 Bad Request', 'ERR Referto troppo lungo (' . strlen($text) . ' byte, massimo ' . $MAX_BYTES . ').');
}

if (!is_dir($DIR)) {
    @mkdir($DIR, 0700, true);
    @file_put_contents($DIR . '/.htaccess', "Require all denied\nDeny from all\n");
}
if (!is_dir($DIR) || !is_writable($DIR)) {
    fail('500 Internal Server Error', 'ERR Cartella non scrivibile.');
}

if (!within_rate($DIR, $RATE_PER_HOUR)) {
    fail('429 Too Many Requests', 'ERR Troppi invii da questo indirizzo.');
}

if (!is_dir($DIR . '/' . $app)) {
    @mkdir($DIR . '/' . $app, 0700, true);
}

$name = date('Ymd-His') . '-' . bin2hex(random_bytes(3)) . '.txt';
$header = '# ' . date('Y-m-d H:i:s') . "\n";
if ($note !== '') {
    $header .= '# nota: ' . str_replace(array("\r", "\n"), ' ', $note) . "\n";
}
$header .= "\n";

if (@file_put_contents($DIR . '/' . $app . '/' . $name, $header . $text, LOCK_EX) === false) {
    fail('500 Internal Server Error', 'ERR Non sono riuscito a salvare il referto.');
}
@chmod($DIR . '/' . $app . '/' . $name, 0600);
sweep($DIR . '/' . $app, $KEEP_DAYS, $MAX_FILES);

echo 'OK ' . $app . '/' . $name . "\n";
