package com.dmdbrands.gurus.weight.features.metricinfo.strings

import com.dmdbrands.gurus.weight.domain.enums.MetricKey
import java.time.format.DateTimeFormatter

/**
 * Strings used in the Metric Info screen.
 */
object MetricInfoStrings {
  const val AppBarTitle = "Body Metrics"
  const val MeasurementNotTaken = "no measurement available"

  const val ResourcesTitle = "Resources"
}

data class MetricInfoResource(
  val title: String,
  val link: String
)

data class MetricInfoContent(
  val title: String,
  val description: String,
  val resources: List<MetricInfoResource> = emptyList()
)

private val metricInfoResources: Map<MetricKey, List<MetricInfoResource>> = mapOf(
  MetricKey.WEIGHT to listOf(
    MetricInfoResource(
      "Harvard School of Public Health",
      "https://www.hsph.harvard.edu/nutritionsource/healthy-weight/",
    ),
    MetricInfoResource(
      "Office of Disease Prevention and Health Promotion",
      "https://health.gov/myhealthfinder/health-conditions/diabetes/aim-healthy-weight",
    ),
  ),
  MetricKey.BMI to listOf(
    MetricInfoResource("CDC Guidelines", "https://www.cdc.gov/bmi/index.html"),
    MetricInfoResource("Cleveland Clinic", "https://my.clevelandclinic.org/health/articles/9464-body-mass-index-bmi"),
  ),
  MetricKey.BODY_FAT to listOf(
    MetricInfoResource("Cleveland Clinic", "https://my.clevelandclinic.org/health/body/24052-adipose-tissue-body-fat"),
    MetricInfoResource(
      "Harvard School of Public Health",
      "https://www.hsph.harvard.edu/nutritionsource/healthy-weight/measuring-fat/",
    ),
  ),
  MetricKey.MUSCLE_MASS to listOf(
    MetricInfoResource("Cleveland Clinic", "https://my.clevelandclinic.org/health/body/21887-muscle"),
    MetricInfoResource(
      "Mayo Clinic",
      "https://www.mayoclinic.org/healthy-lifestyle/fitness/in-depth/strength-training/art-20046670",
    ),
  ),
  MetricKey.BODY_WATER to listOf(
    MetricInfoResource(
      "Merck Manual",
      "https://www.merckmanuals.com/home/hormonal-and-metabolic-disorders/water-balance/about-body-water",
    ),
    MetricInfoResource("Texas A&M Health", "https://vitalrecord.tamu.edu/you-asked-what-is-water-weight/"),
  ),
  MetricKey.HEART_RATE to listOf(
    MetricInfoResource(
      "American Heart Association",
      "https://www.heart.org/en/health-topics/high-blood-pressure/the-facts-about-high-blood-pressure/all-about-heart-rate-pulse",
    ),
    MetricInfoResource(
      "Harvard Medical School",
      "https://www.health.harvard.edu/heart-health/what-your-heart-rate-is-telling-you",
    ),
  ),
  MetricKey.BONE_MASS to listOf(
    MetricInfoResource(
      "Mayo Clinic",
      "https://www.mayoclinic.org/healthy-lifestyle/adult-health/in-depth/bone-health/art-20045060",
    ),
    MetricInfoResource(
      "American Academy of Orthopedic Surgeons",
      "https://orthoinfo.aaos.org/en/staying-healthy/healthy-bones-at-every-age/#:~:text=Most%20people%20will%20reach%20their,proper%20nutrition%20and%20regular%20exercise.",
    ),
  ),
  MetricKey.VISCERAL_FAT to listOf(
    MetricInfoResource("Cleveland Clinic", "https://my.clevelandclinic.org/health/diseases/24147-visceral-fat"),
  ),
  MetricKey.SUBCUTANEOUS_FAT to listOf(
    MetricInfoResource("Cleveland Clinic", "https://my.clevelandclinic.org/health/diseases/23968-subcutaneous-fat"),
    MetricInfoResource("Healthline", "https://www.healthline.com/health/subcutaneous-fat"),
  ),
  MetricKey.PROTEIN to listOf(
    MetricInfoResource(
      "Harvard School of Public Health",
      "https://www.hsph.harvard.edu/nutritionsource/what-should-you-eat/protein/",
    ),
    MetricInfoResource("National Library of Medicine (MedlinePlus)", "https://medlineplus.gov/ency/article/002467.htm"),
  ),
  MetricKey.SKELETAL_MUSCLE to listOf(
    MetricInfoResource("Cleveland Clinic", "https://my.clevelandclinic.org/health/body/21787-skeletal-muscle"),
    MetricInfoResource("National Cancer Institute", "https://training.seer.cancer.gov/anatomy/muscular/structure.html"),
  ),
  MetricKey.BMR to listOf(
    MetricInfoResource("Cleveland Clinic", "https://health.clevelandclinic.org/calories-burned-in-a-day/"),
    MetricInfoResource("Healthline", "https://www.healthline.com/health/what-is-basal-metabolic-rate"),
  ),
  MetricKey.METABOLIC_AGE to listOf(
    MetricInfoResource("Healthline", "https://www.healthline.com/health/exercise-fitness/metabolic-age"),
    MetricInfoResource("Nutrisense", "https://www.nutrisense.io/blog/can-you-change-your-metabolic-age"),
  ),
)

object MetricInfoDescriptions {
  val map: Map<MetricKey, MetricInfoContent> = mapOf(
    MetricKey.WEIGHT to MetricInfoContent(
      title = "Weight",
      description = "Weight is a measurement of your body mass: bones, muscles, organs, and fat. This commonly used metric can help in observing the risk for health conditions such as being underweight, overweight, or obese—those conditions potentially increasing the risk for cardiovascular disease, diabetes, cancer, and other health problems.\n\nMaintaining a healthy body weight via regular exercise and a balanced diet will likely increase overall health and wellbeing.",
      resources = metricInfoResources[MetricKey.WEIGHT] ?: emptyList(),
    ),
    MetricKey.BMI to MetricInfoContent(
      title = "BMI",
      description = "BMI, or body mass index, is a numerical assessment of general body fat that compares weight and height. This metric can be helpful as a screening tool via categories, such as: underweight, normal, overweight, or obese—although there are intrinsic health factors such as muscle mass that BMI does not account for.\n\nAccording to the CDC, basic categories of BMI are as follows: <18.4 is underweight, 18.5-24.9 is normal, 25-29.9 is overweight, and >30 is obese. BMI can help in observing similar health risks as weight.",
      resources = metricInfoResources[MetricKey.BMI] ?: emptyList(),
    ),
    MetricKey.BODY_FAT to MetricInfoContent(
      title = "Body Fat",
      description = "Body fat is an essential component for storing energy, insulating organs, and regulating body temperature. Knowing your body fat percentage can help you assess your risk for certain diseases, track your progress during weight loss or fitness programs, and make informed decisions about your lifestyle choices.\n\nA healthy body fat percentage varies depending on factors such as age, gender, and individual goals.",
      resources = metricInfoResources[MetricKey.BODY_FAT] ?: emptyList(),
    ),
    MetricKey.MUSCLE_MASS to MetricInfoContent(
      title = "Muscle Mass",
      description = "Muscle mass refers to the amount of muscle tissue in your body.  A higher muscle mass is generally associated with improved strength, endurance, and physical performance. Tracking muscle mass allows you to monitor whether you're losing or gaining fat or muscle, and make necessary adjustments to your diet and exercise routine to preserve or increase muscle mass.\n\nMuscle mass is closely linked to metabolic health. Having more muscle can boost your metabolism, leading to improved weight management and better overall health.",
      resources = metricInfoResources[MetricKey.MUSCLE_MASS] ?: emptyList(),
    ),
    MetricKey.BODY_WATER to MetricInfoContent(
      title = "Body Water",
      description = "Body water is the water content of your body. This comprises up to 60% of your total body weight. Body water is essential, since nearly every body system requires water to function properly.\n\nThe healthy range for your body water percentage will depend on a number of factors, including age, sex, and fitness level. The average adult man will have a body water percentage of around 60%, while the average adult woman’s body water percentage is closer to 55%.",
      resources = metricInfoResources[MetricKey.BODY_WATER] ?: emptyList(),
    ),
    MetricKey.HEART_RATE to MetricInfoContent(
      title = "Heart Rate",
      description = "Your heart rate is a measure of how many times your heart beats in a minute.\n\nYour heart rate increases during activity, and decreases when you rest.\n\nHeart rate is also a good measure of overall cardiovascular health. Generally, a low resting heart rate is a sign of a strong, healthy heart, while a higher resting heart rate indicates a lower level of overall physical fitness. By engaging in regular aerobic exercise, you can work to increase your cardiovascular health, and lower your resting heart rate.",
      resources = metricInfoResources[MetricKey.HEART_RATE] ?: emptyList(),
    ),
    MetricKey.BONE_MASS to MetricInfoContent(
      title = "Bone Mass",
      description = "Bone mass is the measure of the bony tissue in your body. This bony tissue is made up of calcium and other minerals.\n\nBone mass is essential to maintaining a healthy skeletal system. Having adequate bone mass helps prevent fractures, while low bone mass can lead to osteoporosis and an increased risk of injury.\n\nBone mass decreases with age. In order to maintain healthy bone mass as you get older, it’s important to eat a balanced diet, get enough calcium, and engage in regular, weight bearing exercise.",
      resources = metricInfoResources[MetricKey.BONE_MASS] ?: emptyList(),
    ),
    MetricKey.VISCERAL_FAT to MetricInfoContent(
      title = "Visceral Fat",
      description = "Visceral fat is the belly fat found deep in your abdominal cavity. This fat surrounds your stomach, liver, intestines, and other organs.\n\nVisceral fat plays an active role in how your organs function. Some visceral fat is necessary, but too much can lead to serious health problems, such as heart disease, diabetes, and stroke.\n\nVisceral fat can be reduced through diet and exercise. By reducing calories, cutting sugar, and engaging in regular physical activity, you can reduce your visceral fat and lower your risk of disease.",
      resources = metricInfoResources[MetricKey.VISCERAL_FAT] ?: emptyList(),
    ),
    MetricKey.SUBCUTANEOUS_FAT to MetricInfoContent(
      title = "Subcutaneous Fat",
      description = "Subcutaneous fat is the fat found just below your skin. This is the fat that you can grab and squeeze with your fingers, and it tends to collect around the butt, thighs, hips, and belly.\n\nSubcutaneous fat is less dangerous than visceral fat, but having too much subcutaneous fat is often a sign that you have too much visceral fat, as well. For this reason, it is still closely associated with heart disease, diabetes, stroke, and other serious diseases.\n\nLike visceral fat, you can reduce the amount of subcutaneous fat that you have through a balanced diet and regular exercise.",
      resources = metricInfoResources[MetricKey.SUBCUTANEOUS_FAT] ?: emptyList(),
    ),
    MetricKey.PROTEIN to MetricInfoContent(
      title = "Protein",
      description = "Protein is a major building block of the human body. It makes up everything from your muscles to your hair and skin, and without enough protein, your body can’t function properly. Typically, protein makes up between 14-16% of your total body mass.\n\nMost adults get enough protein in their diets, but it is important to look for high quality sources of protein. Nuts, legumes, fish, and poultry are all excellent sources of lean protein that give your body the proteins needed to grow and function, without too many unhealthy fats or sugars. This helps with building muscle and maintaining a healthy metabolism, while preventing excess fat buildup.",
      resources = metricInfoResources[MetricKey.PROTEIN] ?: emptyList(),
    ),
    MetricKey.SKELETAL_MUSCLE to MetricInfoContent(
      title = "Skeletal Muscle",
      description = "Skeletal muscles are the muscles that connect your bones, and allow you to move. Unlike the heart and other organs, skeletal muscles are voluntary muscles that you can control.\n\nSkeletal muscles are important for a number of reasons. For one, strong, healthy muscles help prevent injury, and allow you to do more. Additionally, muscle mass aids in metabolism. By building muscle mass, it becomes easier to maintain a healthy weight, since the body burns more calories, even during rest.",
      resources = metricInfoResources[MetricKey.SKELETAL_MUSCLE] ?: emptyList(),
    ),
    MetricKey.BMR to MetricInfoContent(
      title = "BMR (Basal Metabolic Rate)",
      description = "BMR stands for Basal Metabolic Rate.  Basal metabolic rate refers to the amount of energy that your body uses during rest.\n\nHaving a high BMR means that your body burns a lot of calories, even when you aren’t doing anything active. This, in turn, makes it easier to maintain a healthy weight.\n\nBMR is impacted by a number of factors, including age, sex, build, and genetics. However, there are things that you can do to improve your BMR. One of the best ways to do this is through regular exercise: Regular exercise helps build muscle, and having more muscle mass means that your body will burn more calories, even when you aren’t moving around.",
      resources = metricInfoResources[MetricKey.BMR] ?: emptyList(),
    ),
    MetricKey.METABOLIC_AGE to MetricInfoContent(
      title = "Metabolic Age",
      description = "Metabolic Age isn’t an exact science. It’s more of a fitness term than a medical one, and focuses on how your metabolic rate compares to that of your peers. It's calculated by looking at your BMR (amount of calories your body can burn during rest) relative to your fitness level and how that compares to averages in different age ranges.\n\nYour BMR (Basal Metabolic Rate) begins to drop in your late 20’s, but staying fit and active, can lower your metabolic age even as you get older. Particularly, by building muscle mass, you can raise your BMR and therefore lower your metabolic age. Progress is more important than the number so track this metric with that in mind.",
      resources = metricInfoResources[MetricKey.METABOLIC_AGE] ?: emptyList(),
    ),
  )
}

val fullDateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy") // e.g. "January 9, 2025"
val fullMonthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy") // e.g. "January 2025"
