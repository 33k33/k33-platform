package com.k33.platform.app.vault

import arrow.core.raise.nullable
import com.k33.platform.app.vault.coingecko.CoinGeckoClient
import com.k33.platform.app.vault.pdf.getBalanceReport
import com.k33.platform.app.vault.stripe.StripeService
import com.k33.platform.filestore.FileStoreService
import com.k33.platform.fireblocks.service.FireblocksService
import com.k33.platform.identity.auth.gcp.FirebaseAuthService
import com.k33.platform.user.UserId
import com.k33.platform.utils.logging.NotifySlack
import com.k33.platform.utils.logging.getLogger
import io.firestore4k.typed.FirestoreClient
import io.firestore4k.typed.div
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.util.Locale

object VaultService {

    private val logger by getLogger()

    private val firestoreClient by lazy { FirestoreClient() }

    suspend fun UserId.getVaultAssets(currency: String?): List<VaultAsset> {
        val vaultApp = firestoreClient.get(inVaultAppContext())
            ?: throw NotFoundException("Not registered to K33 Vault service")

        return getVaultAssets(
            vaultApp = vaultApp,
            currency = currency
        )
    }

    internal suspend fun getVaultAssets(
        vaultApp: VaultApp,
        currency: String?,
    ): List<VaultAsset> {
        val vaultAccount = FireblocksService.fetchVaultAccountById(
            vaultAccountId = vaultApp.vaultAccountId,
        )

        if (vaultAccount == null) {
            logger.warn(
                NotifySlack.ALERTS,
                "No vault account (id: ${vaultApp.vaultAccountId}) found in Fireblocks"
            )
            return emptyList()
        }

        val baseCurrency = currency ?: vaultApp.currency

        val cryptoCurrencyToBalanceMap = vaultAccount
            .assets
            ?.mapNotNull { vaultAsset ->
                nullable {
                    vaultAsset.id.bind() to vaultAsset.available.bind().toDouble()
                }
            }
            ?.toMap()
            ?: emptyMap()

        return if (cryptoCurrencyToBalanceMap.isNotEmpty()) {
            val cryptoCurrencyList = cryptoCurrencyToBalanceMap
                .filterValues { it > 0 }
                .keys
                .toList()
            val fxMap = CoinGeckoClient.getFxRates(
                baseCurrency = baseCurrency,
                currencyList = cryptoCurrencyList
            )
            cryptoCurrencyToBalanceMap
                .map { (currency, balance) ->
                    VaultAsset(
                        id = currency,
                        available = balance,
                        rate = nullable {
                            Amount(
                                value = fxMap[currency]?.rate.bind(),
                                currency = baseCurrency,
                            )
                        },
                        fiatValue = nullable {
                            Amount(
                                value = fxMap[currency]?.rate.bind() * balance,
                                currency = baseCurrency,
                            )
                        },
                        dailyPercentChange = fxMap[currency]
                            ?.percentChangeIn24hr,
                    )
                }
                .sortedByDescending { it.fiatValue?.value }
        } else {
            emptyList()
        }
    }

    suspend fun UserId.getVaultAddresses(
        vaultAssetId: String
    ): List<VaultAssetAddress> {
        val vaultApp = firestoreClient.get(inVaultAppContext())
            ?: throw NotFoundException("Not registered to K33 Vault service")

        return FireblocksService.fetchVaultAssetAddresses(
            vaultAccountId = vaultApp.vaultAccountId,
            vaultAssetId = vaultAssetId,
        ).mapNotNull {
            nullable {
                VaultAssetAddress(
                    assetId = vaultAssetId,
                    address = it.address.bind(),
                    addressFormat = it.addressFormat,
                    legacyAddress = it.address,
                    tag = it.tag
                )
            }
        }
    }

    suspend fun UserId.getVaultAppSettings(): VaultAppSettings {
        val vaultApp = firestoreClient.get(inVaultAppContext())
            ?: throw NotFoundException("Not registered to K33 Vault service")
        return VaultAppSettings(
            currency = vaultApp.currency,
        )
    }

    suspend fun UserId.updateVaultAppSettings(settings: VaultAppSettings) {
        val vaultApp = firestoreClient.get(inVaultAppContext())
            ?: throw NotFoundException("Not registered to K33 Vault service")
        val updatedVaultApp = vaultApp.copy(
            currency = settings.currency,
        )
        firestoreClient.put(inVaultAppContext(), updatedVaultApp)
    }

    suspend fun UserId.register(
        vaultApp: VaultApp,
    ) {
        firestoreClient.put(inVaultAppContext(), vaultApp)
    }

    suspend fun generateVaultAccountBalanceReports(
        date: LocalDate,
        mode: Mode,
    ) {
        val (userIdToDetailsMap, userIdToVaultAssetBalanceListMap) = coroutineScope {
            val userIdToDetailsMap = StripeService
                .getAllCustomerDetails()
                .mapNotNull { customerDetails ->
                    val userIdString = FirebaseAuthService.findUserIdOrNull(customerDetails.email)
                    if (userIdString != null) {
                        UserId(userIdString) to customerDetails
                    } else {
                        logger.warn("User (email: ${customerDetails.email}) not registered to K33 Platform")
                        null
                    }
                }
                .toMap()
            val userIdToVaultAccountIdMap = userIdToDetailsMap
                .mapValues { (_, customerDetails) -> customerDetails.email }
                .mapNotNull { (userId, email) ->
                    val vaultApp = firestoreClient.get(userId.inVaultAppContext())
                    if (vaultApp == null) {
                        logger.warn("User (email: $email) not registered to K33 Vault App")
                        null
                    } else {
                        userId to vaultApp.vaultAccountId
                    }
                }
                .toMap()
            val userIdToVaultAssetBalanceListMap = userIdToVaultAccountIdMap
                .map { (userId, vaultAccountId) ->
                    async {
                        if (mode.fetch) {
                            // fetch
                            userId to fetchFireblocksVaultAssets(vaultAccountId).also {
                                if (mode.store) {
                                    // store
                                    userId.storeVaultAssetBalanceList(it, date)
                                }
                            }
                        } else {
                            // mode.load == true
                            userId to userId.fetchVaultAssetBalanceList(date)
                        }
                    }
                }
                .awaitAll()
                .toMap()
            userIdToDetailsMap to userIdToVaultAssetBalanceListMap
        }
        coroutineScope {
            val assetIdList = userIdToVaultAssetBalanceListMap
                .values
                .flatMap { it.map(VaultAssetBalance::asset) }
                .distinct()
            val assetIdToRateMap = if (mode.useCurrentFxRate) {
                CoinGeckoClient.getFxRates(
                    baseCurrency = "nok",
                    currencyList = assetIdList,
                ).mapValues { (_, fxRate) -> fxRate.rate }
            } else {
                assetIdList.map { assetId ->
                    async {
                        assetId to CoinGeckoClient.getHistoricalFxRates(
                            cryptoCurrency = assetId,
                            date = date,
                            fiatCurrency = "nok",
                        )
                    }
                }.awaitAll()
                    .toMap()
            }
            userIdToVaultAssetBalanceListMap
                .mapValues { (_, vaultAssetBalanceList) ->
                    vaultAssetBalanceList
                        .mapNotNull { vaultAssetBalance ->
                            nullable {
                                val available = vaultAssetBalance.available?.toDoubleOrNull().bind()
                                val rate = assetIdToRateMap[vaultAssetBalance.asset].bind()
                                VaultAsset(
                                    id = vaultAssetBalance.asset,
                                    available = available,
                                    rate = Amount(rate, "NOK"),
                                    fiatValue = Amount(rate * available, "NOK"),
                                    dailyPercentChange = null
                                )
                            }
                        }
                }
                .map { (userId, vaultAssets) ->
                    val reportFileContents = getBalanceReport(
                        name = userIdToDetailsMap[userId]?.name ?: "",
                        address = userIdToDetailsMap[userId]?.address?.let { address ->
                            listOfNotNull(
                                address.line1,
                                address.line2,
                                "${address.postalCode?.plus(" ") ?: ""}${address.city ?: ""}",
                                address.state,
                                nullable { Locale.of("", address.country.bind()).displayName }
                            )
                        } ?: emptyList(),
                        date = date,
                        vaultAssets = vaultAssets,
                    )
                    saveBalanceReport(
                        userId = userId,
                        fileName = "$date.pdf",
                        contents = reportFileContents,
                    )
                }
        }
    }

    private suspend fun fetchFireblocksVaultAssets(
        vaultAccountId: String,
    ): List<VaultAssetBalance> = coroutineScope {
        val vaultAccount = FireblocksService.fetchVaultAccountById(
            vaultAccountId = vaultAccountId,
        )
        when {
            vaultAccount == null -> {
                logger.warn("No vault account (id: $vaultAccountId) found in Fireblocks")
                emptyList()
            }

            vaultAccount.assets.isNullOrEmpty() -> {
                logger.warn("No assets found in vault account (id: $vaultAccountId) in Fireblocks")
                emptyList()
            }

            else -> {
                vaultAccount
                    .assets
                    ?.mapNotNull { fireblocksVaultAsset ->
                        nullable {
                            VaultAssetBalance(
                                asset = fireblocksVaultAsset.id.bind(),
                                total = fireblocksVaultAsset.total,
                                available = fireblocksVaultAsset.available,
                                pending = fireblocksVaultAsset.pending,
                                frozen = fireblocksVaultAsset.frozen,
                                lockedAmount = fireblocksVaultAsset.lockedAmount,
                                staked = fireblocksVaultAsset.staked,
                            )
                        }
                    }
                    ?: emptyList()
            }
        }
    }

    private suspend fun saveBalanceReport(
        userId: UserId,
        fileName: String,
        contents: ByteArray,
    ) {
        FileStoreService.upload(
            bucketConfigId = "firebase",
            filePath = "users/$userId/balanceReports/$fileName",
            contents = contents,
        )
    }

    private suspend fun UserId.fetchVaultAssetBalanceList(
        date: LocalDate,
    ) = firestoreClient.getAll(
        inVaultAppContext() / balanceRecords / date.toString() / assets
    )

    private suspend fun UserId.storeVaultAssetBalanceList(
        vaultAssetBalanceList: List<VaultAssetBalance>,
        date: LocalDate,
    ) = coroutineScope {
        vaultAssetBalanceList
            .map { vaultAssetBalance ->
                async {
                    firestoreClient.put(
                        inVaultAppContext() / balanceRecords / date.toString() / assets / vaultAssetBalance.asset,
                        vaultAssetBalance
                    )
                }
            }
            .awaitAll()
    }
}