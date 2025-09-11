//
//  MetricContent.swift
//  meApp
//
//  Created by Barath Chittibabu on 20/06/25.
//


enum MetricContentRepository {
    static func content(for metric: BodyMetric) -> MetricDetailContent {
        switch metric {
        case .weight:
            return MetricDetailContent(
                header: "Why Weight?",
                messages: [
                    "Weight is a measurement of your body mass: bones, muscles, organs, and fat. This commonly used metric can help in observing the risk for health conditions such as being underweight, overweight, or obese—those conditions potentially increasing the risk for cardiovascular disease, diabetes, cancer, and other health problems.",
                    "Maintaining a healthy body weight via regular exercise and a balanced diet will likely increase overall health and wellbeing."
                ],
                resources: [
                    MetricResource(title: "Harvard School of Public Health", link: "https://www.hsph.harvard.edu/nutritionsource/healthy-weight/"),
                    MetricResource(title: "Office of Disease Prevention and Health Promotion", link: "https://health.gov/myhealthfinder/health-conditions/diabetes/aim-healthy-weight")
                ])
        case .bmi:
            return MetricDetailContent(
                header: "Why BMI?",
                messages: [
                    "BMI, or body mass index, is a numerical assessment of general body fat that compares weight and height. This metric can be helpful as a screening tool via categories, such as: underweight, normal, overweight, or obese—although there are intrinsic health factors such as muscle mass that BMI does not account for.",
                    "According to the CDC, basic categories of BMI are as follows: <18.4 is underweight, 18.5-24.9 is normal, 25-29.9 is overweight, and >30 is obese. BMI can help in observing similar health risks as weight."
                ],
                resources: [
                    MetricResource(title: "CDC Guidelines", link: "https://www.cdc.gov/bmi/index.html"),
                    MetricResource(title: "Cleveland Clinic", link: "https://my.clevelandclinic.org/health/articles/9464-body-mass-index-bmi")
                ])
        case .bodyFat:
            return MetricDetailContent(
                header: "Why Body Fat?",
                messages: [
                    "Body fat is an essential component for storing energy, insulating organs, and regulating body temperature. Knowing your body fat percentage can help you assess your risk for certain diseases, track your progress during weight-loss or fitness programs, and make informed decisions about your lifestyle choices.",
                    "A healthy body fat percentage varies depending on factors such as age, gender, and individual goals."
                ],
                resources: [
                    MetricResource(title: "Cleveland Clinic", link: "https://my.clevelandclinic.org/health/body/24052-adipose-tissue-body-fat"),
                    MetricResource(title: "Harvard School of Public Health", link: "https://www.hsph.harvard.edu/nutritionsource/healthy-weight/measuring-fat/")
                ])
        case .muscleMass:
            return MetricDetailContent(
                header: "Why Muscle Mass?",
                messages: [
                    "Muscle mass refers to the amount of muscle tissue in your body. A higher muscle mass is generally associated with improved strength, endurance, and physical performance. Tracking muscle mass allows you to monitor whether you're losing or gaining fat or muscle, and make necessary adjustments to your diet and exercise routine to preserve or increase muscle mass.",
                    "Muscle mass is closely linked to metabolic health. Having more muscle can boost your metabolism, leading to improved weight management and better overall health."
                ],
                resources: [
                    MetricResource(title: "Cleveland Clinic", link: "https://my.clevelandclinic.org/health/body/21887-muscle"),
                    MetricResource(title: "Mayo Clinic", link: "https://www.mayoclinic.org/healthy-lifestyle/fitness/in-depth/strength-training/art-20046670")
                ])
        case .water:
            return MetricDetailContent(
                header: "Why Body Water?",
                messages: [
                    "Body water is the water content of your body. This comprises up to 60% of your total body weight. Body water is essential, since nearly every body system requires water to function properly.",
                    "The healthy range for your body-water percentage will depend on a number of factors, including age, sex, and fitness level. The average adult man will have a body-water percentage of around 60%, while the average adult woman's body-water percentage is closer to 55%."
                ],
                resources: [
                    MetricResource(title: "Merck Manual", link: "https://www.merckmanuals.com/home/hormonal-and-metabolic-disorders/water-balance/about-body-water"),
                    MetricResource(title: "Texas A&M Health", link: "https://vitalrecord.tamhsc.edu/you-asked-what-is-water-weight/")
                ])
        case .pulse:
            return MetricDetailContent(
                header: "Why Heart Rate?",
                messages: [
                    "Your heart rate is a measure of how many times your heart beats in a minute.",
                    "Your heart rate increases during activity, and decreases when you rest.",
                    "Heart rate is also a good measure of overall cardiovascular health. Generally, a low resting heart rate is a sign of a strong, healthy heart, while a higher resting heart rate indicates a lower level of overall physical fitness. By engaging in regular aerobic exercise, you can work to increase your cardiovascular health and lower your resting heart rate."
                ],
                resources: [
                    MetricResource(title: "American Heart Association", link: "https://www.heart.org/en/health-topics/high-blood-pressure/the-facts-about-high-blood-pressure/all-about-heart-rate-pulse"),
                    MetricResource(title: "Harvard Medical School", link: "https://www.health.harvard.edu/heart-health/what-your-heart-rate-is-telling-you")
                ])
        case .boneMass:
            return MetricDetailContent(
                header: "Why Bone Mass?",
                messages: [
                    "Bone mass is the measure of the bony tissue in your body. This bony tissue is made up of calcium and other minerals.",
                    "Bone mass is essential to maintaining a healthy skeletal system. Having adequate bone mass helps prevent fractures, while low bone mass can lead to osteoporosis and an increased risk of injury.",
                    "Bone mass decreases with age. In order to maintain healthy bone mass as you get older, it's important to eat a balanced diet, get enough calcium, and engage in regular, weight-bearing exercise."
                ],
                resources: [
                    MetricResource(title: "Mayo Clinic",
                                   link: "https://www.mayoclinic.org/healthy-lifestyle/adult-health/in-depth/bone-health/art-20045060"),
                    MetricResource(title: "American Academy of Orthopedic Surgeons",
                                   link: "https://orthoinfo.aaos.org/en/staying-healthy/healthy-bones-at-every-age/")
                ])
        case .visceralFatLevel:
            return MetricDetailContent(
                header: "Why Visceral Fat?",
                messages: [
                    "Visceral fat is the belly fat found deep in your abdominal cavity. This fat surrounds your stomach, liver, intestines, and other organs.",
                    "Visceral fat plays an active role in how your organs function. Some visceral fat is necessary, but too much can lead to serious health problems, such as heart disease, diabetes, and stroke.",
                    "Visceral fat can be reduced through diet and exercise. By reducing calories, cutting sugar, and engaging in regular physical activity, you can reduce your visceral fat and lower your risk of disease."
                ],
                resources: [
                    MetricResource(title: "Cleveland Clinic", link: "https://my.clevelandclinic.org/health/diseases/24147-visceral-fat"),
                    MetricResource(title: "Johns Hopkins School of Medicine", link: "https://www.hopkinsmedicine.org/gim/_documents/Faculty-Resource/The%20Skinny%20on%20Visceral%20Fat.pdf")
                ])
        case .subcutaneousFatPercent:
            return MetricDetailContent(
                header: "Why Subcutaneous Fat?",
                messages: [
                    "Subcutaneous fat is the fat found just below your skin. This is the fat that you can grab and squeeze with your fingers, and it tends to collect around the butt, thighs, hips, and belly.",
                    "Subcutaneous fat is less dangerous than visceral fat, but having too much subcutaneous fat is often a sign that you have too much visceral fat, as well. For this reason, it is still closely associated with heart disease, diabetes, stroke, and other serious diseases.",
                    "Like visceral fat, you can reduce the amount of subcutaneous fat that you have through a balanced diet and regular exercise."
                ],
                resources: [
                    MetricResource(title: "Cleveland Clinic", link: "https://my.clevelandclinic.org/health/diseases/23968-subcutaneous-fat"),
                    MetricResource(title: "Healthline", link: "https://www.healthline.com/health/subcutaneous-fat")
                ])
        case .proteinPercent:
            return MetricDetailContent(
                header: "Why Protein?",
                messages: [
                    "Protein is a major building block of the human body. It makes up everything from your muscles to your hair and skin, and without enough protein, your body can't function properly. Typically, protein makes up between 14-16% of your total body mass.",
                    "Most adults get enough protein in their diets, but it is important to look for high-quality sources of protein. Nuts, legumes, fish, and poultry are all excellent sources of lean protein that give your body the proteins needed to grow and function without too many unhealthy fats or sugars. This helps with building muscle and maintaining a healthy metabolism, while preventing excess fat buildup."
                ],
                resources: [
                    MetricResource(title: "Harvard School of Public Health", link: "https://www.hsph.harvard.edu/nutritionsource/what-should-you-eat/protein/"),
                    MetricResource(title: "National Library of Medicine (MedlinePlus)", link: "https://medlineplus.gov/ency/article/002467.htm")
                ])
        case .skeletalMusclePercent:
            return MetricDetailContent(
                header: "Why Skeletal Muscle?",
                messages: [
                    "Skeletal muscles are the muscles that connect your bones, and allow you to move. Unlike the heart and other organs, skeletal muscles are voluntary muscles that you can control.",
                    "Skeletal muscles are important for a number of reasons. For one, strong, healthy muscles help prevent injury and allow you to do more. Additionally, muscle mass aids in metabolism. By building muscle mass, it becomes easier to maintain a healthy weight, since the body burns more calories, even during rest."
                ],
                resources: [
                    MetricResource(title: "Cleveland Clinic", link: "https://my.clevelandclinic.org/health/body/21787-skeletal-muscle"),
                    MetricResource(title: "National Cancer Institute", link: "https://training.seer.cancer.gov/anatomy/muscular/structure.html")
                ])
        case .bmr:
            return MetricDetailContent(
                header: "Why BMR?",
                messages: [
                    "BMR stands for Basal Metabolic Rate. Basal metabolic rate refers to the amount of energy that your body uses during rest.",
                    "Having a high BMR means that your body burns a lot of calories, even when you aren't doing anything active. This, in turn, makes it easier to maintain a healthy weight.",
                    "BMR is impacted by a number of factors, including age, sex, build, and genetics. However, there are things that you can do to improve your BMR. One of the best ways to do this is through regular exercise: Regular exercise helps build muscle, and having more muscle mass means that your body will burn more calories, even when you aren't moving around."
                ],
                resources: [
                    MetricResource(title: "Cleveland Clinic", link: "https://health.clevelandclinic.org/calories-burned-in-a-day/"),
                    MetricResource(title: "Healthline", link: "https://www.healthline.com/health/what-is-basal-metabolic-rate")
                ])
        case .metabolicAge:
            return MetricDetailContent(
                header: "Why Metabolic Age?",
                messages: [
                    "Metabolic Age isn't an exact science. It's more of a fitness term than a medical one, and focuses on how your metabolic rate compares to that of your peers. It's calculated by looking at your BMR (amount of calories your body can burn during rest) relative to your fitness level and how that compares to averages in different age ranges.",
                    "Your BMR begins to drop in your late 20's, but staying fit and active can lower your metabolic age even as you get older. Particularly, by building muscle mass, you can raise your BMR and therefore lower your metabolic age. Progress is more important than the number, so track this metric with that in mind."
                ],
                resources: [
                    MetricResource(title: "Healthline", link: "https://www.healthline.com/health/exercise-fitness/metabolic-age"),
                    MetricResource(title: "Nutrisense", link: "https://www.nutrisense.io/blog/can-you-change-your-metabolic-age")
                ])
        }
    }
}
