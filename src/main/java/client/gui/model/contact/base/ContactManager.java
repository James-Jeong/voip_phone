package client.gui.model.contact.base;

import config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.AppInstance;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @class public class ContactManager
 * @brief ContactManager class
 */
public class ContactManager {

    private static final Logger logger = LoggerFactory.getLogger(ContactManager.class);

    private static final int MAX_CONTACT_NUM = 500;

    private static ContactManager contactManager = null;

    private final LinkedHashSet<ContactInfo> contactSet;
    private final ReentrantLock contactSetLock = new ReentrantLock();

    ////////////////////////////////////////////////////////////////////////////////

    public ContactManager() {
        contactSet = new LinkedHashSet<>(MAX_CONTACT_NUM);

        try {
            File contactFile = getContactFile();
            if (contactFile != null) {
                BufferedReader inFile = new BufferedReader(new FileReader(contactFile));
                String data;

                while ((data = inFile.readLine()) != null) {
                    //logger.debug("[CONTACT] [{}]", data);
                    String[] content = data.split(",");
                    if (content.length != ContactInfo.CONTACT_CONTENT_NUM) {
                        continue;
                    }

                    String name = content[0];
                    String email = content[1];
                    String mdn = content[2];
                    String sipIp = content[3];
                    int sipPort = Integer.parseInt(content[4]);

                    addContactInfo(
                            name,
                            email,
                            mdn,
                            sipIp,
                            sipPort
                    );
                }
            } else {
                ConfigManager configManager = AppInstance.getInstance().getConfigManager();
                String contactFileName = configManager.getContactPath();
                logger.warn("Fail to load the contacts. Contact file is not exist. (path={})", contactFileName);
            }
        } catch (Exception e) {
            logger.warn("Fail to load the contacts.", e);
        }
    }

    public static ContactManager getInstance () {
        if (contactManager == null) {
            contactManager = new ContactManager();
        }

        return contactManager;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public int getContactSetSize() {
        try {
            contactSetLock.lock();

            return contactSet.size();
        } catch (Exception e) {
            logger.warn("Fail to get the contact set size.", e);
            return 0;
        } finally {
            contactSetLock.unlock();
        }
    }

    public ContactInfo addContactInfo(String name, String email, String mdn, String sipIp, int sipPort) {
        if (mdn == null || sipIp == null || sipPort <= 0) {
            logger.warn("Fail to add the contact info. (name={}, email={}, mdn={}, sipIp={}, sipPort={})", name, email, mdn, sipIp, sipPort);
            return null;
        }

        try {
            contactSetLock.lock();

            ContactInfo contactInfo = new ContactInfo(name, email, mdn, sipIp, sipPort);
            for (ContactInfo curContactInfo : contactSet) {
                if (curContactInfo != null) {
                    if (curContactInfo.getMdn().equals(mdn)) {
                        return null;
                    }
                }
            }

            if (contactSet.add(contactInfo)) {
                return contactInfo;
            } else {
                return null;
            }
        } catch (Exception e) {
            logger.warn("Fail to add the contact info. (name={}, email={}, mdn={}, sipIp={}, sipPort={})", name, email, mdn, sipIp, sipPort, e);
            return null;
        } finally {
            contactSetLock.unlock();
        }
    }

    public void deleteContactInfo(ContactInfo contactInfo) {
        if (contactInfo == null) {
            return;
        }

        try {
            contactSetLock.lock();

            if (contactSet.remove(contactInfo)) {
                logger.debug("Success to delete the contact info. ({})", contactInfo);
            } else {
                logger.warn("Fail to delete the contact info. ({})", contactInfo);
            }
        } catch (Exception e) {
            logger.warn("Fail to delete the contact info. (contactInfo={})", contactInfo, e);
        } finally {
            contactSetLock.unlock();
        }
    }

    public ContactInfo getContactInfoByMdn(String mdn) {
        if (mdn == null) { return null; }

        try {
            contactSetLock.lock();

            for (ContactInfo curContactInfo : contactSet) {
                if (curContactInfo != null) {
                    if (curContactInfo.getMdn().equals(mdn)) {
                        return curContactInfo;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            logger.warn("Fail to get the contact info by the mdn. (mdn={})", mdn, e);
            return null;
        } finally {
            contactSetLock.unlock();
        }
    }

    public ContactInfo getContactInfoByIpPort(String ip, int port) {
        if (ip == null || port <= 0 || port > 65535) { return null; }

        try {
            contactSetLock.lock();

            for (ContactInfo curContactInfo : contactSet) {
                if (curContactInfo != null) {
                    if (curContactInfo.getSipIp().equals(ip) && (curContactInfo.getSipPort() == port)) {
                        return curContactInfo;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            logger.warn("Fail to get the contact info by the mdn. (ip={}, port={})", ip, port, e);
            return null;
        } finally {
            contactSetLock.unlock();
        }
    }

    public LinkedHashSet<ContactInfo> cloneContactInfoSet() {
        try {
            contactSetLock.lock();

            return (LinkedHashSet<ContactInfo>) contactSet.clone();
        } catch (Exception e) {
            logger.warn("Fail to clone the contact set.", e);
            return null;
        } finally {
            contactSetLock.unlock();
        }
    }

    public void clearContactInfoSet() {
        try {
            contactSetLock.lock();

            contactSet.clear();
            logger.debug("Success to clear the contact set.");
        } catch (Exception e) {
            logger.warn("Fail to clear the contact set.", e);
        } finally {
            contactSetLock.unlock();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    public File getContactFile() {
        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        String contactFileName = configManager.getContactPath();
        if (contactFileName != null) {
            File contactFile = new File(contactFileName);
            if (contactFile.isFile() && contactFile.exists()) {
                return contactFile;
            } else {
                try {
                    if (contactFile.createNewFile()) {
                        return contactFile;
                    } else {
                        logger.warn("Fail to create new contact path. ({})", contactFileName);
                        return null;
                    }
                } catch (Exception e) {
                    logger.warn("Fail to create new contact file. ({})", contactFileName, e);
                    return null;
                }
            }
        } else {
            logger.warn("Fail to get the contact file path.");
        }

        return null;
    }

    public boolean addContactInfoToFile(ContactInfo contactInfo) {
        if (contactInfo == null) {
            return false;
        }

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        String contactFileName = configManager.getContactPath();

        File contactFile = getContactFile();
        if (contactFile == null) {
            logger.warn("Fail to add the contact info to the file. (path={})", contactFileName);
            return false;
        }

        String contactDataString = System.lineSeparator() + contactInfo;

        Path contactFilePath = contactFile.toPath();
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(contactFilePath, StandardOpenOption.APPEND))) {
            out.write(contactDataString.getBytes());
        } catch (IOException e) {
            logger.warn("Fail to add the contact data string. ({}, path={})", contactInfo, contactFileName);
            return false;
        }

        return true;
    }

    public boolean reWriteContactInfoFromFile(LinkedHashSet<ContactInfo> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return false;
        }

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        String contactFileName = configManager.getContactPath();

        File contactFile = getContactFile();
        if (contactFile == null) {
            logger.warn("Fail to modify the contact info from the file. (path={})", contactFileName);
            return false;
        }


        try {
            try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(contactFile))) {
                int index = 0;
                for (ContactInfo contactInfo : dataList) {
                    String contactDataString = contactInfo.toString();

                    if (index++ < dataList.size() - 1) {
                        contactDataString += "\n";
                    }

                    bufferedOutputStream.write(contactDataString.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                logger.warn("Fail to write the contact info from the file. (path={})", contactFileName, e);
                return false;
            }
        } catch (Exception e) {
            logger.warn("Fail to modify the contact info from the file. (path={})", contactFileName, e);
            return false;
        }

        return true;
    }

    public boolean removeContactInfoFromFile(ContactInfo contactInfo) {
        if (contactInfo == null) {
            return false;
        }

        ConfigManager configManager = AppInstance.getInstance().getConfigManager();
        String contactFileName = configManager.getContactPath();

        File contactFile = getContactFile();
        if (contactFile == null) {
            logger.warn("Fail to remove the contact info from the file. (path={})", contactFileName);
            return false;
        }

        String contactDataString = contactInfo.toString();

        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(contactFile, "rw");
            List<String> dataList = new ArrayList<>();
            String data;

            while ((data = randomAccessFile.readLine()) != null) {
                if (!data.equals(contactDataString)) {
                    dataList.add(data);
                }
            }

            try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(contactFile))) {
                int index = 0;
                for (String curData : dataList) {
                    if (index++ < dataList.size() - 1) {
                        curData += "\n";
                    }

                    bufferedOutputStream.write(curData.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                logger.warn("Fail to write the contact info from the file. (path={})", contactFileName, e);
                return false;
            }
        } catch (Exception e) {
            logger.warn("Fail to remove the contact info from the file. (path={})", contactFileName, e);
            return false;
        }

        return true;
    }

    public boolean clearFile() {
        File contactFile = getContactFile();
        if (contactFile == null) {
            return false;
        }

        try {
            PrintWriter writer = new PrintWriter(contactFile);
            writer.print("");
            writer.close();
        } catch (Exception e) {
            ConfigManager configManager = AppInstance.getInstance().getConfigManager();
            String contactFileName = configManager.getContactPath();
            logger.warn("Fail to clear the contact file. (path={})", contactFileName);
            return false;
        }

        return true;
    }

}
