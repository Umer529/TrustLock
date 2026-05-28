const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { initializeApp }     = require('firebase-admin/app');
const { getFirestore }      = require('firebase-admin/firestore');
const { getMessaging }      = require('firebase-admin/messaging');

initializeApp();

/**
 * Triggered when a new ApprovalRequest document is created.
 * Looks up the guardian's FCM token by email and sends them a push notification.
 */
exports.onApprovalRequestCreated = onDocumentCreated(
    'approvalRequests/{requestId}',
    async (event) => {
        const request   = event.data.data();
        const requestId = event.params.requestId;
        const { userId, type, payload, guardianEmail } = request;

        if (!guardianEmail) {
            console.warn('No guardianEmail on request', requestId);
            return;
        }

        const db = getFirestore();

        // Fetch the requesting user's display name
        const userSnap = await db.collection('users').doc(userId).get();
        if (!userSnap.exists) {
            console.warn('User not found:', userId);
            return;
        }
        const userName = userSnap.data().name || 'Someone';

        // Find the guardian's account by email to get their FCM token
        const guardianQuery = await db.collection('users')
            .where('email', '==', guardianEmail)
            .limit(1)
            .get();

        if (guardianQuery.empty) {
            console.warn('Guardian not found for email:', guardianEmail);
            return;
        }

        const fcmToken = guardianQuery.docs[0].data().fcmToken;
        if (!fcmToken) {
            console.warn('Guardian has no FCM token — cannot notify');
            return;
        }

        // Build a human-readable description of what the user wants to do
        let actionText = 'make a change';
        if (type === 'CHANGE_LIMIT' && payload) {
            const appName = payload.appName || 'an app';
            const oldMin  = payload.currentLimitMinutes;
            const newMin  = payload.newLimitMinutes;
            actionText = oldMin
                ? `change ${appName} limit from ${oldMin} to ${newMin} minutes`
                : `add a ${newMin}-minute limit for ${appName}`;
        } else if (type === 'UNINSTALL') {
            actionText = 'uninstall ScreenPact';
        }

        const deepLink = `screenpact://approve?requestId=${requestId}`;

        const message = {
            token: fcmToken,
            notification: {
                title: 'ScreenPact Approval Request',
                body:  `${userName} wants to ${actionText}. Tap to approve or deny.`,
            },
            data: {
                requestId,
                deepLink,
                click_action: 'OPEN_GUARDIAN_APPROVAL',
            },
        };

        try {
            await getMessaging().send(message);
            console.log('FCM notification sent for request:', requestId);
        } catch (err) {
            console.error('FCM send failed:', err);
        }
    }
);
